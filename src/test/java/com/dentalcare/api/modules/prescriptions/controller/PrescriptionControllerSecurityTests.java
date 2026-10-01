package com.dentalcare.api.modules.prescriptions.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionPatientResponse;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionProfessionalResponse;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import com.dentalcare.api.modules.prescriptions.model.PrescriptionStatus;
import com.dentalcare.api.modules.prescriptions.service.PrescriptionService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PrescriptionController.class, PatientPrescriptionController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PrescriptionControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PrescriptionService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void administrativeRoutesRequireAuthenticationAndPermissions() throws Exception {
        UUID patientId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/patients/{id}/prescriptions", patientId))
                .andExpect(status().isUnauthorized());

        token("reader", UUID.randomUUID(), "PRESCRIPTION_READ");
        when(service.findByPatient(patientId, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/v1/patients/{id}/prescriptions", patientId)
                        .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/patients/{id}/prescriptions", patientId)
                        .header("Authorization", "Bearer reader")
                        .contentType("application/json").content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUsesAuthenticatedProfessionalAndReturnsLocation() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        PrescriptionResponse response = response(patientId, professionalId);
        token("dentist", professionalId, "PRESCRIPTION_CREATE");
        when(service.create(eq(patientId), eq(professionalId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/patients/{id}/prescriptions", patientId)
                        .header("Authorization", "Bearer dentist")
                        .contentType("application/json").content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        verify(service).create(eq(patientId), eq(professionalId), any());
    }

    @Test
    void patientSelfServiceUsesJwtIdentityAndRejectsStaff() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        token("patient", userId, "ROLE_PATIENT");
        when(service.findMine(userId, 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(service.findMineById(userId, prescriptionId))
                .thenReturn(response(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/patients/me/prescriptions/{id}", prescriptionId)
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());
        verify(service).findMine(userId, 0, 20);
        verify(service).findMineById(userId, prescriptionId);

        token("staff", UUID.randomUUID(), "ROLE_DENTIST", "PRESCRIPTION_READ");
        mockMvc.perform(get("/api/v1/patients/me/prescriptions")
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotUseAdministrativePrescriptionRoutes() throws Exception {
        token("patient", UUID.randomUUID(), "ROLE_PATIENT");
        mockMvc.perform(get("/api/v1/patients/{id}/prescriptions", UUID.randomUUID())
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private String validBody() {
        return """
                {
                  "medication": "Amoxicilina",
                  "presentation": "Tableta 500 mg",
                  "dosage": "500 mg",
                  "frequency": "Cada 8 horas",
                  "duration": "7 días",
                  "instructions": "Tomar después de comer"
                }
                """;
    }

    private PrescriptionResponse response(UUID patientId, UUID professionalId) {
        return new PrescriptionResponse(UUID.randomUUID(),
                new PrescriptionPatientResponse(patientId, "PAC-001", "Paciente Prueba"),
                new PrescriptionProfessionalResponse(professionalId, "Dra. Prueba"),
                "Amoxicilina", "Tableta 500 mg", "500 mg", "Cada 8 horas", "7 días",
                "Tomar después de comer", Instant.parse("2026-10-01T12:00:00Z"),
                PrescriptionStatus.ISSUED);
    }
}
