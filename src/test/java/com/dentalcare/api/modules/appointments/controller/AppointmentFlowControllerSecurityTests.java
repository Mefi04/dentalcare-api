package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.appointments.dto.request.FirstAppointmentIntakeRequest;
import com.dentalcare.api.modules.appointments.dto.response.FirstAppointmentReceipt;
import com.dentalcare.api.modules.appointments.service.*;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PublicAppointmentRequestController.class,
        PublicAppointmentAvailabilityController.class, AdministrativeAppointmentRequestController.class,
        AppointmentContactController.class, FirstAppointmentConfirmationController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class AppointmentFlowControllerSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean PublicFirstAppointmentService intake;
    @MockitoBean GeneralDentistryAvailabilityService availability;
    @MockitoBean AppointmentRequestService requests;
    @MockitoBean AppointmentContactService contacts;
    @MockitoBean FirstAppointmentConfirmationService confirmation;
    @MockitoBean JwtService jwtService;
    @MockitoBean com.dentalcare.api.security.ratelimit.RateLimitService rateLimitService;

    @Test
    void publicIntakeReturnsAcknowledgementWithoutConversationToken() throws Exception {
        UUID id = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        when(intake.submit(any(FirstAppointmentIntakeRequest.class), eq(key)))
                .thenReturn(new FirstAppointmentReceipt(id, false, "Reception will call"));
        mockMvc.perform(post("/api/v1/public/appointment-requests")
                .header("Idempotency-Key", key).contentType("application/json")
                .content(validPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(id.toString()))
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.conversationToken").doesNotExist())
                .andExpect(jsonPath("$.cui").doesNotExist());
    }

    @Test
    void obsoleteConversationRoutesAreNotPublic() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/public/appointment-requests/{id}/conversation", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/public/appointment-requests/{id}/decision", id)
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionRoutesRequireAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/appointment-requests/{id}/contact-attempts", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/appointment-requests/{id}/telephone-confirmation", id)
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String validPayload() {
        return """
                {"fullName":"Maria Lopez","cui":"1234567890123","birthDate":"1990-01-01",
                 "gender":"FEMALE","phone":"+502 5555-0101","department":"Guatemala",
                 "municipality":"Guatemala","address":"Zona 1",
                 "emergencyName":"Juan Lopez","emergencyPhone":"+502 5555-0102",
                 "requestedAt":"2099-10-02T15:00:00Z","privacyAccepted":true,
                 "privacyNoticeVersion":"2026-10"}
                """;
    }
}
