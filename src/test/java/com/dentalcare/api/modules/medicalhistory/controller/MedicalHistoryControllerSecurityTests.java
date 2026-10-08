package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryService;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MedicalHistoryController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class MedicalHistoryControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MedicalHistoryService medicalHistoryService;

    @MockitoBean
    private MedicalHistoryWorkflowService workflowService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void readRequiresAuthenticationAndClinicalReadPermission() throws Exception {
        UUID patientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/patients/{patientId}/medical-history", patientId))
                .andExpect(status().isUnauthorized());

        token("secretary-token", "ROLE_SECRETARY", "PATIENT_READ");
        mockMvc.perform(get("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer secretary-token"))
                .andExpect(status().isForbidden());

        token("clinical-read-token", "MEDICAL_HISTORY_READ");
        when(medicalHistoryService.findByPatientId(patientId)).thenReturn(response(patientId));
        mockMvc.perform(get("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer clinical-read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.allergies[0]").value("Penicilina"));

        verify(medicalHistoryService).findByPatientId(patientId);
    }

    @Test
    void legacyImportRequiresDentistOnlyCompatibilityPermission() throws Exception {
        UUID patientId = UUID.randomUUID();
        String body = validRequest();

        token("read-token", "MEDICAL_HISTORY_READ");
        mockMvc.perform(put("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer read-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        token("update-token", "MEDICAL_HISTORY_LEGACY_IMPORT");
        when(workflowService.importLegacy(eq(patientId), any(), any())).thenReturn(response(patientId));
        mockMvc.perform(put("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer update-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"));
    }

    @Test
    void updateRejectsInvalidListsAndOversizedValues() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("update-token", "MEDICAL_HISTORY_LEGACY_IMPORT");

        mockMvc.perform(put("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer update-token")
                        .contentType("application/json")
                        .content("""
                                {"allergies":[""],"currentMedications":[],"relevantConditions":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(put("/api/v1/patients/{patientId}/medical-history", patientId)
                        .header("Authorization", "Bearer update-token")
                        .contentType("application/json")
                        .content("""
                                {"allergies":null,"currentMedications":[],"relevantConditions":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private MedicalHistoryResponse response(UUID patientId) {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        return new MedicalHistoryResponse(patientId, List.of("Penicilina"), List.of("Metformina"),
                List.of("Diabetes tipo 2"), "Controlada", now, now, PatientHealthStatus.UPDATED);
    }

    private String validRequest() {
        return """
                {"allergies":["Penicilina"],"currentMedications":["Metformina"],
                 "relevantConditions":["Diabetes tipo 2"],"observations":"Controlada"}
                """;
    }
}
