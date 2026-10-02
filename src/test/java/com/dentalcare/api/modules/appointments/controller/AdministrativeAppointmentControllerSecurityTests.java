package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentPatientResponse;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.service.AdministrativeAppointmentService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdministrativeAppointmentController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class AdministrativeAppointmentControllerSecurityTests {

    private static final Instant FUTURE = Instant.parse("2099-10-01T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministrativeAppointmentService administrativeAppointmentService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void listRequiresAuthenticationAndAdministrativeAgendaRole() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized());

        token("patient-token", "ROLE_PATIENT");
        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden());

        token("secretary-token", "ROLE_SECRETARY");
        when(administrativeAppointmentService.findAll(
                null, null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response())));
        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer secretary-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patient.name").value("Paciente Agenda"));
    }

    @Test
    void listAcceptsAllAdministrativeFilters() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        Instant from = FUTURE.minusSeconds(3600);
        Instant to = FUTURE.plusSeconds(3600);
        token("dentist-token", "ROLE_DENTIST");
        when(administrativeAppointmentService.findAll(
                from, to, patientId, professionalId, AppointmentStatus.SCHEDULED, 1, 10))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer dentist-token")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("patientId", patientId.toString())
                        .param("professionalId", professionalId.toString())
                        .param("status", "SCHEDULED")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void createIsRestrictedToAdministratorOrSecretaryAndValidatesRequest() throws Exception {
        token("assistant-token", "ROLE_ASSISTANT");
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer assistant-token")
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());

        token("secretary-token", "ROLE_SECRETARY");
        when(administrativeAppointmentService.create(any())).thenReturn(response());
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer secretary-token")
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer secretary-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rescheduleIsRestrictedToAdministratorOrSecretary() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        token("dentist-token", "ROLE_DENTIST");
        mockMvc.perform(patch("/api/v1/appointments/{id}/schedule", appointmentId)
                        .header("Authorization", "Bearer dentist-token")
                        .contentType("application/json")
                        .content("{\"scheduledAt\":\"2099-10-01T16:00:00Z\"}"))
                .andExpect(status().isForbidden());

        token("administrator-token", "ROLE_ADMINISTRATOR");
        when(administrativeAppointmentService.reschedule(eq(appointmentId), any()))
                .thenReturn(response());
        mockMvc.perform(patch("/api/v1/appointments/{id}/schedule", appointmentId)
                        .header("Authorization", "Bearer administrator-token")
                        .contentType("application/json")
                        .content("{\"scheduledAt\":\"2099-10-01T16:00:00Z\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void statusUpdateIsAvailableToClinicalAgendaRoles() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        token("assistant-token", "ROLE_ASSISTANT");
        when(administrativeAppointmentService.updateStatus(
                any(), eq(appointmentId), eq(AppointmentStatus.COMPLETED))).thenReturn(response());

        mockMvc.perform(patch("/api/v1/appointments/{id}/status", appointmentId)
                        .header("Authorization", "Bearer assistant-token")
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private AdministrativeAppointmentResponse response() {
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        return new AdministrativeAppointmentResponse(
                UUID.randomUUID(),
                new AdministrativeAppointmentPatientResponse(
                        patientId, "PAC-001", "Paciente Agenda", "5555-0101"),
                new AppointmentProfessionalResponse(professionalId, "Dra. Andrea Ruiz"),
                FUTURE,
                AppointmentStatus.SCHEDULED,
                FUTURE.minusSeconds(3600),
                FUTURE.minusSeconds(3600));
    }

    private String validCreateBody() {
        return """
                {"patientId":"%s","professionalId":"%s","scheduledAt":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), FUTURE);
    }
}
