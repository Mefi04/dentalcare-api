package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.model.SlotStatus;
import com.dentalcare.api.modules.appointments.service.AppointmentAvailabilityService;
import com.dentalcare.api.modules.assistant.client.GeminiApiClient;
import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.assistant.dto.response.PublicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceImplTests {

    @Mock
    private GeminiApiClient geminiClient;

    @Mock
    private AppointmentAvailabilityService availabilityService;

    private AssistantServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new AssistantServiceImpl(
                geminiClient,
                availabilityService,
                new AssistantPrivacySanitizer(),
                objectMapper
        );
    }

    @Test
    void throwsBadRequestWhenAiConsentNotAccepted() {
        var request = new PublicAssistantRequest("Hola", null, false);
        assertThatThrownBy(() -> service.processMessage(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consent");
    }

    @Test
    void gracefulFallbackWhenGeminiClientNotConfigured() {
        when(geminiClient.isConfigured()).thenReturn(false);

        var request = new PublicAssistantRequest("¿Cuánto cuesta una limpieza?", null, true);
        PublicAssistantResponse response = service.processMessage(request);

        assertThat(response.reply()).contains("costo de cada procedimiento");
        assertThat(response.action()).isEqualTo("PROVIDE_INFORMATION");
        assertThat(response.suggestedSlots()).isEmpty();
    }

    @Test
    void answersServicesInquiryEvenWhenAvailabilityDateIsSupplied() {
        when(geminiClient.isConfigured()).thenReturn(false);

        LocalDate date = LocalDate.parse("2026-10-30");
        var request = new PublicAssistantRequest("¿Qué servicios ofrecen en la clínica?", date, true);
        PublicAssistantResponse response = service.processMessage(request);

        assertThat(response.reply()).contains("odontología general y tratamientos dentales");
        assertThat(response.action()).isEqualTo("PROVIDE_INFORMATION");
        assertThat(response.suggestedSlots()).isEmpty();
    }

    @Test
    void givesSpecificGuidanceForClinicOpeningHoursWhenGeminiIsUnavailable() {
        var response = service.processMessage(new PublicAssistantRequest("¿Cuáles son sus horarios de atención presencial?", null, true));
        assertThat(response.reply()).contains("horario general de atención");
        assertThat(response.reply()).doesNotContain("¡Hola!");
    }

    @Test
    void doesNotReplaceSpecificGeminiAnswerWithGenericReply() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.generateContent(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiApiClient.GeminiResponse("Atendemos de lunes a sábado, de 8:00 a. m. a 5:00 p. m.", null)));
        var response = service.processMessage(new PublicAssistantRequest("¿Cuáles son sus horarios?", null, true));
        assertThat(response.reply()).contains("Atendemos de lunes a sábado");
        assertThat(response.reply()).doesNotContain("temporalmente limitada");
    }

    @Test
    void extractsDateFromChatMessageAndReturnsAvailabilitySlots() {
        when(geminiClient.isConfigured()).thenReturn(false);
        LocalDate date = LocalDate.parse("2099-10-20");
        var slot = new AppointmentSlotResponse(Instant.parse("2099-10-20T14:00:00Z"), "08:00", SlotStatus.AVAILABLE, 1, 1);
        when(availabilityService.getAvailableSlotsForGemini(date)).thenReturn(List.of(slot));
        var response = service.processMessage(new PublicAssistantRequest("¿Qué horas tienen disponibles el 20/10/2099?", null, true));
        assertThat(response.reply()).contains("08:00");
        assertThat(response.suggestedSlots()).containsExactly(slot);
        verify(availabilityService).getAvailableSlotsForGemini(date);
    }

    @Test
    void sanitizesSensitivePiiBeforeSendingToGemini() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.generateContent(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiApiClient.GeminiResponse("Con gusto te apoyamos.", null)));

        var request = new PublicAssistantRequest("Mi DPI es 1234567890123 y mi teléfono es 55551234", null, true);
        PublicAssistantResponse response = service.processMessage(request);

        verify(geminiClient).generateContent(anyString(), org.mockito.ArgumentMatchers.argThat(msg ->
                !msg.contains("1234567890123") && !msg.contains("55551234") && msg.contains("[DPI_REDACTED]")));

        assertThat(response.reply()).contains("Nota de seguridad");
    }

    @Test
    void executesAvailabilityToolWhenGeminiRequestsFunctionCall() {
        when(geminiClient.isConfigured()).thenReturn(true);
        LocalDate targetDate = LocalDate.parse("2026-10-20");

        var functionCall = new GeminiApiClient.FunctionCall(
                GeminiApiClient.TOOL_GET_SLOTS, Map.of("date", "2026-10-20"));
        when(geminiClient.generateContent(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiApiClient.GeminiResponse(null, functionCall)));

        var slot = new AppointmentSlotResponse(
                Instant.parse("2026-10-20T14:00:00Z"), "08:00", SlotStatus.AVAILABLE, 1, 1);
        when(availabilityService.getAvailableSlotsForGemini(targetDate)).thenReturn(List.of(slot));

        when(geminiClient.sendToolResponse(anyString(), anyString(), eq(GeminiApiClient.TOOL_GET_SLOTS), anyString()))
                .thenReturn(Optional.of("Tenemos horarios disponibles a las 08:00 para primera cita."));

        var request = new PublicAssistantRequest("¿Tienen disponibilidad el 2026-10-20?", null, true);
        PublicAssistantResponse response = service.processMessage(request);

        assertThat(response.reply()).contains("08:00");
        assertThat(response.suggestedSlots()).hasSize(1);
        assertThat(response.action()).isEqualTo("GUIDE_TO_INTAKE");
    }
}
