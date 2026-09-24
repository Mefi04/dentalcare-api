package com.dentalcare.api.modules.patients.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
import com.dentalcare.api.modules.patients.dto.response.CreatePatientAccessResponse;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.service.PatientService;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class})
class PatientControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void listRequiresAndAcceptsPatientReadPermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("read-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("PATIENT_READ")));
        when(patientService.search(0, 20, null)).thenReturn(new PageImpl<>(List.of(response(id))));

        mockMvc.perform(get("/api/v1/patients").header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].code").value("PAC-00001"));

        verify(patientService).search(0, 20, null);
    }

    @Test
    void createRejectsAuthenticatedCallerWithoutPatientCreatePermission() throws Exception {
        when(jwtService.parseAccessToken("read-only-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("PATIENT_READ")));

        mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer read-only-token")
                        .contentType("application/json")
                        .content("""
                                {"name":"Maria Perez","dpi":"2987451200101","birthDate":"1990-01-01",
                                 "gender":"FEMALE","phone":"5555-1234"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void currentPatientUsesJwtUserIdAndRequiresPatientRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        token("patient-token", userId, "ROLE_PATIENT");
        when(patientService.findCurrentPatient(userId)).thenReturn(response(patientId));

        mockMvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patientId.toString()));

        verify(patientService).findCurrentPatient(userId);
        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isUnauthorized());
        token("staff-token", UUID.randomUUID(), "ROLE_SECRETARY");
        mockMvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void portalAccessCreationRequiresAdministratorRole() throws Exception {
        UUID patientId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/patients/{id}/access", patientId))
                .andExpect(status().isUnauthorized());

        token("patient-token", UUID.randomUUID(), "ROLE_PATIENT");
        mockMvc.perform(post("/api/v1/patients/{id}/access", patientId)
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden());

        UUID administratorId = UUID.randomUUID();
        token("administrator-token", administratorId, "ROLE_ADMINISTRATOR");
        when(patientService.createAccess(patientId)).thenReturn(new CreatePatientAccessResponse(patientId,
                UUID.randomUUID(), "patient-" + UUID.randomUUID(), UserStatus.PENDING_ACTIVATION, "temporary"));
        mockMvc.perform(post("/api/v1/patients/{id}/access", patientId)
                        .header("Authorization", "Bearer administrator-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private PatientResponse response(UUID id) {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        return new PatientResponse(id, "PAC-00001", "Maria Perez", "2987451200101", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "5555-1234", null, null, null, null, null, "Maria Perez", "CF", null,
                null, null, null, now, now);
    }
}
