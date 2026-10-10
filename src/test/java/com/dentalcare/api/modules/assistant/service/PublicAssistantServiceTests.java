package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.service.GeneralDentistryAvailabilityService;
import com.dentalcare.api.modules.assistant.dto.request.PublicAssistantRequest;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.service.PublicInformationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAssistantServiceTests {
    @Mock GeminiGateway gemini;
    @Mock PublicInformationService information;
    @Mock GeneralDentistryAvailabilityService availability;

    @Test
    void sendsOnlyPublicClinicContextAndReturnsGeneralAnswer() {
        when(information.getClinic()).thenReturn(new PublicClinicResponse(
                "DentalCare", "55550000", "info@example.test", "Centro", "Guatemala", "L-V"));
        when(information.getServices()).thenReturn(List.of(new PublicServiceResponse(
                "GENERAL", "Odontología general", "Consulta", "Revisión", 30)));
        when(gemini.generate(anyString(), anyString())).thenReturn("Puede solicitar una primera cita.");

        var response = service().answer(new PublicAssistantRequest("¿Cómo pido mi primera cita?", null, true));

        assertThat(response.answer()).isEqualTo("Puede solicitar una primera cita.");
        assertThat(response.notice()).contains("no sustituye");
        verify(gemini).generate(contains("nunca reserva ni confirma citas"),
                contains("Odontología general"));
    }

    @Test
    void rejectsObviousIdentifiersBeforeCallingProvider() {
        assertThatThrownBy(() -> service().answer(new PublicAssistantRequest(
                "Mi correo es persona@example.com", null, true))).isInstanceOf(BadRequestException.class);
        verify(gemini, never()).generate(anyString(), anyString());
    }

    @Test
    void requiresAiProcessingConsent() {
        assertThatThrownBy(() -> service().answer(new PublicAssistantRequest(
                "¿Qué servicios ofrecen?", null, false))).isInstanceOf(BadRequestException.class);
        verify(gemini, never()).generate(anyString(), anyString());
    }

    @Test
    void urgentMessageGetsImmediateAnswerWithoutProvider() {
        var response = service().answer(new PublicAssistantRequest(
                "Tengo dificultad para respirar, mi teléfono es 55551234", null, true));
        assertThat(response.answer()).contains("atención de urgencia");
        verify(gemini, never()).generate(anyString(), anyString());
    }

    private PublicAssistantService service() {
        return new PublicAssistantService(gemini, information, availability);
    }
}
