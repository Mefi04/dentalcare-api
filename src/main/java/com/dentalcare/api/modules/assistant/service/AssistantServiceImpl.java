package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.service.AppointmentAvailabilityService;
import com.dentalcare.api.modules.assistant.client.GeminiApiClient;
import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.assistant.dto.response.PublicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
            Eres el asistente virtual oficial de DentalCare, una clínica odontológica moderna ubicada en Guatemala.
            Tu objetivo es atender a visitantes que desean orientación sobre la clínica, servicios dentales y solicitud de primera cita.

            REGLAS ESTRICTAS DE SEGURIDAD Y PRIVACIDAD:
            1. NUNCA solicites ni pidas al usuario datos personales sensibles (como DPI/CUI, teléfono, dirección, correo, tarjetas o historial médico) dentro de esta conversación.
            2. Si el paciente desea agendar su cita, explícale que debe seleccionar su horario y completar sus datos personales directamente en el formulario seguro de solicitud.
            3. Si el usuario te proporciona datos personales accidentalmente, recuérdale con amabilidad que no debe compartirlos en el chat y que utilice los campos seguros del formulario.
            4. Tú NO tienes la capacidad de confirmar citas ni modificar la base de datos. Toda solicitud enviada queda en estado pendiente y el personal administrativo de la clínica se comunicará para su confirmación definitiva.
            5. NUNCA inventes horarios disponibles. Solo informa sobre los horarios devueltos por la herramienta de disponibilidad.
            6. Sé cordial, empático, profesional y conciso en español.
            """;

    private final GeminiApiClient geminiApiClient;
    private final AppointmentAvailabilityService availabilityService;
    private final AssistantPrivacySanitizer privacySanitizer;
    private final ObjectMapper objectMapper;

    public AssistantServiceImpl(GeminiApiClient geminiApiClient,
                                AppointmentAvailabilityService availabilityService,
                                AssistantPrivacySanitizer privacySanitizer,
                                ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.availabilityService = availabilityService;
        this.privacySanitizer = privacySanitizer;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public PublicAssistantResponse processMessage(PublicAssistantRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BadRequestException("Message is required");
        }
        if (request.aiProcessingAccepted() == null || !request.aiProcessingAccepted()) {
            throw new BadRequestException("Explicit consent for AI processing is required");
        }

        var sanitization = privacySanitizer.sanitize(request.message());
        String sanitizedUserMessage = sanitization.sanitizedText();

        List<AppointmentSlotResponse> suggestedSlots = new ArrayList<>();
        LocalDate queryDate = request.availabilityDate() != null
                ? request.availabilityDate() : extractAvailabilityDate(sanitizedUserMessage);

        String lowerUserMsg = sanitizedUserMessage.toLowerCase(java.util.Locale.ROOT);
        boolean isInfoQuery = containsAny(lowerUserMsg, "servicio", "servicios", "tratamiento", "tratamientos", "limpieza", "ortodoncia", "endodoncia", "blanqueamiento", "extracción", "extraccion", "caries");

        if (queryDate != null && !isInfoQuery && (request.availabilityDate() != null || isAppointmentIntent(sanitizedUserMessage))) {
            try {
                suggestedSlots = availabilityService.getAvailableSlotsForGemini(queryDate);
            } catch (Exception e) {
                log.warn("Could not retrieve availability for date {}: {}", queryDate, e.getMessage());
            }
        }

        if (needsAvailability(sanitizedUserMessage, request.availabilityDate()) && queryDate == null) {
            return new PublicAssistantResponse("Para consultar horarios de una cita necesito la fecha deseada. Elige la fecha en el calendario de solicitud y te mostraremos únicamente los bloques disponibles.",
                    List.of(), "GUIDE_TO_INTAKE");
        }

        if (!geminiApiClient.isConfigured()) {
            return fallbackResponse(sanitization.piiDetected(), queryDate, suggestedSlots, false, sanitizedUserMessage);
        }

        try {
            Optional<GeminiApiClient.GeminiResponse> firstTurn = geminiApiClient.generateContent(
                    SYSTEM_PROMPT, sanitizedUserMessage);

            if (firstTurn.isEmpty()) {
                return fallbackResponse(sanitization.piiDetected(), queryDate, suggestedSlots, true, sanitizedUserMessage);
            }

            GeminiApiClient.GeminiResponse response = firstTurn.get();

            if (response.functionCall() != null && GeminiApiClient.TOOL_GET_SLOTS.equals(response.functionCall().name())) {
                Object dateArg = response.functionCall().arguments().get("date");
                if (dateArg != null) {
                    try {
                        LocalDate toolDate = LocalDate.parse(dateArg.toString());
                        suggestedSlots = availabilityService.getAvailableSlotsForGemini(toolDate);
                        queryDate = toolDate;

                        String toolResultJson = objectMapper.writeValueAsString(suggestedSlots);
                        Optional<String> followUpText = geminiApiClient.sendToolResponse(
                                SYSTEM_PROMPT, sanitizedUserMessage, GeminiApiClient.TOOL_GET_SLOTS, toolResultJson);

                        String finalReply = followUpText.orElse(
                                "He consultado la disponibilidad para " + toolDate + ". Puedes ver las franjas horarias disponibles a continuación.");

                        if (sanitization.piiDetected()) {
                            finalReply += "\n\n(Nota de seguridad: Recuerda no ingresar tu DPI o teléfono en el chat; por favor regístralos en el formulario seguro).";
                        }

                        return new PublicAssistantResponse(finalReply, suggestedSlots, "GUIDE_TO_INTAKE");
                    } catch (DateTimeParseException e) {
                        log.warn("Gemini provided invalid date argument: {}", dateArg);
                    }
                }
            }

            String replyText = response.text() != null && !response.text().isBlank()
                    ? response.text() : localReply(sanitizedUserMessage, queryDate, suggestedSlots);

            if (sanitization.piiDetected()) {
                replyText += "\n\n(Nota de seguridad: Recuerda no ingresar tu DPI o teléfono en el chat; por favor regístralos en el formulario seguro).";
            }

            String action = (!suggestedSlots.isEmpty() || isAppointmentIntent(sanitizedUserMessage))
                    ? "GUIDE_TO_INTAKE" : "PROVIDE_INFORMATION";

            return new PublicAssistantResponse(replyText, suggestedSlots, action);
        } catch (Exception e) {
            log.warn("Error in AssistantServiceImpl interaction: {}", e.getMessage());
            return fallbackResponse(sanitization.piiDetected(), queryDate, suggestedSlots, true, sanitizedUserMessage);
        }
    }

    private boolean isAppointmentIntent(String message) {
        String lower = message.toLowerCase();
        return lower.contains("cita") || lower.contains("agendar") || lower.contains("horario")
                || lower.contains("disponib") || lower.contains("reserv");
    }

    private boolean needsAvailability(String message, LocalDate explicitDate) {
        if (explicitDate != null || message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return (lower.contains("cita") || lower.contains("agendar") || lower.contains("disponib"))
                && (lower.contains("fecha") || lower.contains("disponib") || lower.contains("horario"));
    }

    private PublicAssistantResponse fallbackResponse(boolean piiDetected, LocalDate date,
                                                      List<AppointmentSlotResponse> slots, boolean providerUnavailable,
                                                      String originalMessage) {
        StringBuilder sb = new StringBuilder();
        if (piiDetected) {
            sb.append("Por motivos de seguridad, no compartas datos personales (como DPI o teléfono) en el chat. ");
        }

        String lowerMessage = originalMessage != null ? originalMessage.toLowerCase(java.util.Locale.ROOT) : "";
        boolean asksForServices = containsAny(lowerMessage, "servicio", "servicios", "tratamiento", "tratamientos", "limpieza", "ortodoncia", "endodoncia", "blanqueamiento", "extracción", "extraccion", "caries");
        boolean asksForClinicHours = containsAny(lowerMessage, "horario de atención", "horario de atencion", "horarios de atención", "horarios de atencion", "abren", "abierto", "atención presencial", "atencion presencial");

        if (!asksForServices && !asksForClinicHours && isAppointmentIntent(originalMessage) && date != null && slots != null && !slots.isEmpty()) {
            sb.append(localReply("disponibilidad", date, slots));
            return new PublicAssistantResponse(sb.toString(), slots, "GUIDE_TO_INTAKE");
        }

        sb.append(localReply(originalMessage, date, slots));
        if (providerUnavailable) sb.append(" (La respuesta automática está temporalmente limitada; puedes continuar usando el formulario de cita.)");
        return new PublicAssistantResponse(sb.toString(), List.of(), "PROVIDE_INFORMATION");
    }

    private String localReply(String message, LocalDate date, List<AppointmentSlotResponse> slots) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (containsAny(text, "horario", "horarios", "abren", "abierto", "atención presencial", "atencion presencial")) {
            return "El horario general de atención debe confirmarse en la sección de contacto/horarios del sitio. Para una cita, selecciona una fecha y consulta los bloques disponibles; pueden ser distintos del horario de apertura.";
        }
        if (containsAny(text, "servicio", "servicios", "tratamiento", "tratamientos", "limpieza", "ortodoncia", "endodoncia", "blanqueamiento", "extracción", "extraccion", "caries")) {
            return "Podemos orientarte sobre odontología general y tratamientos dentales. El equipo clínico confirma la disponibilidad y el costo de cada procedimiento; no compartas síntomas ni datos personales en este chat. Para atención, solicita una primera cita desde el formulario.";
        }
        if (date != null && slots != null && !slots.isEmpty()) {
            List<String> times = slots.stream().filter(s -> s.status() == com.dentalcare.api.modules.appointments.model.SlotStatus.AVAILABLE)
                    .map(AppointmentSlotResponse::localTime).distinct().toList();
            return times.isEmpty() ? "No aparecen bloques disponibles para esa fecha. Prueba con otra fecha en el calendario." :
                    "Para el " + date + " aparecen estos horarios de primera cita: " + times + ". Elige uno en el formulario; la clínica confirmará la solicitud.";
        }
        if (containsAny(text, "cita", "agendar", "reservar", "disponibilidad", "disponible")) {
            return "Con gusto te ayudamos con tu primera cita. Selecciona una fecha en el formulario para consultar horarios y completa tus datos en la sección segura; enviar la solicitud no confirma ni reserva todavía el horario.";
        }
        return "Puedo orientarte sobre servicios dentales y primera cita. ¿Tu consulta es sobre un servicio, el horario de atención o disponibilidad para una fecha específica?";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private LocalDate extractAvailabilityDate(String text) {
        if (text == null) return null;
        var iso = java.util.regex.Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b").matcher(text);
        if (iso.find()) {
            try { return LocalDate.parse(iso.group(1)); }
            catch (DateTimeParseException ignored) { return null; }
        }
        var local = java.util.regex.Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](20\\d{2})\\b").matcher(text);
        if (local.find()) {
            try { return LocalDate.of(Integer.parseInt(local.group(3)), Integer.parseInt(local.group(2)), Integer.parseInt(local.group(1))); }
            catch (DateTimeParseException | NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
