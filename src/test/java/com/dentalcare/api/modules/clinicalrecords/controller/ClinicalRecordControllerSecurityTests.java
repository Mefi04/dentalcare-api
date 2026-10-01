package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalRecordService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ClinicalRecordController.class,
        ClinicalAttentionController.class,
        OdontogramController.class
}, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ClinicalRecordControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ClinicalRecordService clinicalRecordService;
    @MockitoBean private JwtService jwtService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-record", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-history", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clinical-attentions/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions", id)
                .contentType("application/json").content(validAttentionBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/diagnoses", id)
                .contentType("application/json").content(validDiagnosisBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", id)
                .contentType("application/json").content(validEvolutionBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/odontogram/findings", id)
                .contentType("application/json").content(validFindingBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/odontogram", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithReadPermissionCanReadButCannotWrite() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID attentionId = UUID.randomUUID();
        token("reader", "CLINICAL_RECORD_READ");

        when(clinicalRecordService.findAttentionsByPatient(eq(patientId), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalRecordService.findAttentionById(attentionId))
                .thenReturn(sampleAttentionResponse(attentionId, patientId));
        when(clinicalRecordService.findCurrentOdontogram(eq(patientId), any()))
                .thenReturn(new OdontogramResponse(DentitionType.ADULT, Map.of("11", ToothFinding.HEALTHY), List.of()));

        // Allowed reads
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clinical-attentions/{id}", attentionId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attentionId.toString()));

        mockMvc.perform(get("/api/v1/patients/{id}/odontogram", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

        // Denied writes (403 Forbidden)
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions", patientId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validAttentionBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/diagnoses", attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validDiagnosisBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{id}/odontogram/findings", patientId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validFindingBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithWritePermissionCanPerformMutations() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID attentionId = UUID.randomUUID();
        token("writer", "CLINICAL_RECORD_WRITE");

        when(clinicalRecordService.createAttention(eq(patientId), any(), any()))
                .thenReturn(sampleAttentionResponse(attentionId, patientId));
        when(clinicalRecordService.createDiagnosis(eq(attentionId), any(), any()))
                .thenReturn(sampleDiagnosisResponse(attentionId, patientId));
        when(clinicalRecordService.createEvolutionNote(eq(attentionId), any(), any()))
                .thenReturn(sampleEvolutionResponse(attentionId, patientId));
        when(clinicalRecordService.createOdontogramFinding(eq(patientId), any(), any()))
                .thenReturn(sampleFindingResponse(patientId));

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions", patientId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validAttentionBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/diagnoses", attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validDiagnosisBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/{id}/odontogram/findings", patientId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validFindingBody()))
                .andExpect(status().isCreated());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private String validAttentionBody() {
        return """
                {
                    "reason": "Evaluacion de rutina",
                    "clinicalNotes": "Exploracion sin novedades mayores",
                    "nextSteps": "Profilaxis programada"
                }
                """;
    }

    private String validDiagnosisBody() {
        return """
                {
                    "type": "PRIMARY",
                    "description": "Gingivitis inducida por placa"
                }
                """;
    }

    private String validEvolutionBody() {
        return """
                {
                    "consultationDate": "2026-10-01",
                    "procedureSummary": "Profilaxis dental",
                    "note": "Procedimiento completado exitosamente"
                }
                """;
    }

    private String validFindingBody() {
        return """
                {
                    "dentition": "ADULT",
                    "toothCode": "16",
                    "surface": "OCCLUSAL",
                    "finding": "CARIOUS",
                    "observation": "Caries incipiente"
                }
                """;
    }

    private ClinicalAttentionResponse sampleAttentionResponse(UUID attentionId, UUID patientId) {
        return new ClinicalAttentionResponse(
                attentionId, patientId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                null, "Motivo", "Notas", "Siguientes pasos",
                Instant.now(), Instant.now(), Instant.now()
        );
    }

    private ClinicalDiagnosisResponse sampleDiagnosisResponse(UUID attentionId, UUID patientId) {
        return new ClinicalDiagnosisResponse(
                UUID.randomUUID(), patientId, attentionId, null,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                DiagnosisType.PRIMARY, "Gingivitis", Instant.now()
        );
    }

    private ClinicalEvolutionResponse sampleEvolutionResponse(UUID attentionId, UUID patientId) {
        return new ClinicalEvolutionResponse(
                UUID.randomUUID(), patientId, attentionId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                LocalDate.of(2026, 10, 1), "Profilaxis", "Completada", Instant.now()
        );
    }

    private OdontogramFindingResponse sampleFindingResponse(UUID patientId) {
        return new OdontogramFindingResponse(
                UUID.randomUUID(), patientId, null,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries", Instant.now()
        );
    }
}
