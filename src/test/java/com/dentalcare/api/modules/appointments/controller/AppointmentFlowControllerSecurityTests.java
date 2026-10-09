package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.config.*;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.appointments.service.*;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.AssignAppointmentRequestProfessionalRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequestReceipt;
import com.dentalcare.api.security.ratelimit.RateLimitService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.*;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdministrativeAppointmentRequestController.class,
        PatientAppointmentRequestController.class, WaitingRoomController.class,
        PublicAppointmentRequestController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class AppointmentFlowControllerSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean AppointmentRequestService requestService;
    @MockitoBean WaitingRoomService waitingRoomService;
    @MockitoBean JwtService jwtService;
    @MockitoBean RateLimitService rateLimitService;

    @Test
    void publicAppointmentRequestIsAnonymousAndReturnsOnlyReceipt() throws Exception {
        UUID key = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(requestService.createPublic(any(CreatePublicAppointmentRequest.class), eq(key)))
                .thenReturn(new PublicAppointmentRequestReceipt(requestId,
                        "We received your request. The clinic will contact you to confirm or propose a time."));

        mockMvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("""
                                {"fullName":"María López","cui":"1234567890123","phone":"+502 5555-0101",
                                 "email":"maria@example.test","requestedAt":"2099-10-02T15:00:00Z",
                                 "professionalId":null,"reason":"Primera consulta, tarde"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.cui").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void publicAppointmentRequestValidatesRequiredInputsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"fullName":" ","phone":"invalid","requestedAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestService);
    }

    @Test
    void administrativeRequestsRequireSecretaryOrAdministrator() throws Exception {
        mockMvc.perform(get("/api/v1/appointment-requests")).andExpect(status().isUnauthorized());

        token("cashier", "ROLE_CASHIER");
        mockMvc.perform(get("/api/v1/appointment-requests").header("Authorization", "Bearer cashier"))
                .andExpect(status().isForbidden());

        token("secretary", "ROLE_SECRETARY");
        when(requestService.findAll(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/appointment-requests").header("Authorization", "Bearer secretary"))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCanAssignDentistToPublicAppointmentRequestButOtherRolesCannot() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        token("cashier", "ROLE_CASHIER");
        mockMvc.perform(post("/api/v1/appointment-requests/{id}/assign-professional", requestId)
                        .header("Authorization", "Bearer cashier")
                        .contentType("application/json")
                        .content("{\"professionalId\":\"%s\"}".formatted(professionalId)))
                .andExpect(status().isForbidden());

        token("secretary", "ROLE_SECRETARY");
        when(requestService.assignPublicRequestProfessional(any(), eq(requestId), eq(professionalId)))
                .thenReturn(org.mockito.Mockito.mock(AppointmentRequestResponse.class));
        mockMvc.perform(post("/api/v1/appointment-requests/{id}/assign-professional", requestId)
                        .header("Authorization", "Bearer secretary")
                        .contentType("application/json")
                        .content("{\"professionalId\":\"%s\"}".formatted(professionalId)))
                .andExpect(status().isOk());
    }

    @Test
    void patientRequestEndpointsRejectStaffAndAcceptPatient() throws Exception {
        String body = "{\"professionalId\":\"%s\",\"requestedAt\":\"2099-10-02T15:00:00Z\"}"
                .formatted(UUID.randomUUID());
        token("secretary", "ROLE_SECRETARY");
        mockMvc.perform(post("/api/v1/patients/me/appointment-requests")
                        .header("Authorization", "Bearer secretary").contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        token("patient", "ROLE_PATIENT");
        mockMvc.perform(post("/api/v1/patients/me/appointment-requests")
                        .header("Authorization", "Bearer patient").contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void waitingRoomReadAndMutationFollowRoleMatrix() throws Exception {
        token("cashier", "ROLE_CASHIER");
        mockMvc.perform(get("/api/v1/appointments/waiting-room")
                        .header("Authorization", "Bearer cashier"))
                .andExpect(status().isForbidden());

        token("dentist", "ROLE_DENTIST");
        when(waitingRoomService.findAll(isNull(), isNull(), isNull(), eq(0), eq(20))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/appointments/waiting-room")
                        .header("Authorization", "Bearer dentist"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/appointments/{id}/waiting-room/check-in", UUID.randomUUID())
                        .header("Authorization", "Bearer dentist"))
                .andExpect(status().isForbidden());

        token("assistant", "ROLE_ASSISTANT");
        mockMvc.perform(post("/api/v1/appointments/{id}/waiting-room/check-in", UUID.randomUUID())
                        .header("Authorization", "Bearer assistant"))
                .andExpect(status().isCreated());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }
}
