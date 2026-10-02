package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalPreparationRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalPreparationResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.CurrentAttentionResponse;
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

import java.math.BigDecimal;

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
        UUID attentionId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/patients/{id}/clinical-record", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/current-attention", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-history", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clinical-attentions/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}", id, attentionId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions", id)
                .contentType("application/json").content(validAttentionBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/diagnoses", id)
                .contentType("application/json").content(validDiagnosisBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/diagnoses", id, attentionId)
                .contentType("application/json").content(validDiagnosisBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}/diagnoses/{subId}", id, attentionId, subId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", id)
                .contentType("application/json").content(validEvolutionBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/evolution", id, attentionId)
                .contentType("application/json").content(validEvolutionBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}/evolution/{subId}", id, attentionId, subId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/preparations", id)
                .contentType("application/json").content(validPreparationBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/preparations", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/preparations/latest", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/preparations/{subId}", id, subId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/preparation", id, attentionId)
                .contentType("application/json").content(validPreparationBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}/preparation", id, attentionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/odontogram/findings", id)
                .contentType("application/json").content(validFindingBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/odontogram", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithReadPermissionCanReadButCannotWrite() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID attentionId = UUID.randomUUID();
        UUID prepId = UUID.randomUUID();
        token("reader", "CLINICAL_RECORD_READ");

        when(clinicalRecordService.findAttentionsByPatient(eq(patientId), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalRecordService.findAttentionById(attentionId))
                .thenReturn(sampleAttentionResponse(attentionId, patientId));
        when(clinicalRecordService.findAttentionById(patientId, attentionId))
                .thenReturn(sampleAttentionResponse(attentionId, patientId));
        when(clinicalRecordService.findCurrentAttention(patientId))
                .thenReturn(sampleCurrentAttentionResponse(patientId, attentionId));
        when(clinicalRecordService.findPreparationsByPatient(eq(patientId), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(samplePreparationResponse(patientId, attentionId))));
        when(clinicalRecordService.findLatestPreparationByPatient(patientId))
                .thenReturn(samplePreparationResponse(patientId, attentionId));
        when(clinicalRecordService.findPreparationById(patientId, prepId))
                .thenReturn(samplePreparationResponse(patientId, attentionId));
        when(clinicalRecordService.findPreparationByAttention(patientId, attentionId))
                .thenReturn(samplePreparationResponse(patientId, attentionId));
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

        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}", patientId, attentionId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attentionId.toString()));

        mockMvc.perform(get("/api/v1/patients/{id}/current-attention", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCurrentAttention").value(true));

        mockMvc.perform(get("/api/v1/patients/{id}/preparations", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/patients/{id}/preparations/latest", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/patients/{id}/preparations/{prepId}", patientId, prepId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/patients/{id}/clinical-attentions/{attentionId}/preparation", patientId, attentionId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());

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

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/diagnoses", patientId, attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validDiagnosisBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/evolution", patientId, attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{id}/preparations", patientId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validPreparationBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/preparation", patientId, attentionId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(validPreparationBody()))
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

        when(clinicalRecordService.createAttention(eq(patientId), any(CreateClinicalAttentionRequest.class), any(UUID.class)))
                .thenReturn(sampleAttentionResponse(attentionId, patientId));
        when(clinicalRecordService.createDiagnosis(eq(attentionId), any(CreateClinicalDiagnosisRequest.class), any(UUID.class)))
                .thenReturn(sampleDiagnosisResponse(attentionId, patientId));
        when(clinicalRecordService.createDiagnosis(eq(patientId), eq(attentionId), any(CreateClinicalDiagnosisRequest.class), any(UUID.class)))
                .thenReturn(sampleDiagnosisResponse(attentionId, patientId));
        when(clinicalRecordService.createEvolutionNote(eq(attentionId), any(CreateEvolutionNoteRequest.class), any(UUID.class)))
                .thenReturn(sampleEvolutionResponse(attentionId, patientId));
        when(clinicalRecordService.createEvolutionNote(eq(patientId), eq(attentionId), any(CreateEvolutionNoteRequest.class), any(UUID.class)))
                .thenReturn(sampleEvolutionResponse(attentionId, patientId));
        when(clinicalRecordService.createPreparation(eq(patientId), eq(null), any(CreateClinicalPreparationRequest.class), any(UUID.class)))
                .thenReturn(samplePreparationResponse(patientId, null));
        when(clinicalRecordService.createPreparation(eq(patientId), eq(attentionId), any(CreateClinicalPreparationRequest.class), any(UUID.class)))
                .thenReturn(samplePreparationResponse(patientId, attentionId));
        when(clinicalRecordService.createOdontogramFinding(eq(patientId), any(CreateOdontogramFindingRequest.class), any(UUID.class)))
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

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/diagnoses", patientId, attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validDiagnosisBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinical-attentions/{id}/evolution", attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/evolution", patientId, attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validEvolutionBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/{id}/preparations", patientId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validPreparationBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/{id}/clinical-attentions/{attentionId}/preparation", patientId, attentionId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json").content(validPreparationBody()))
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

    private String validPreparationBody() {
        return """
                {
                    "bloodPressure": "120/80",
                    "heartRate": 72,
                    "temperature": 36.5,
                    "weight": 70.2,
                    "observations": "Paciente tranquilo y normotenso"
                }
                """;
    }

    private ClinicalPreparationResponse samplePreparationResponse(UUID patientId, UUID attentionId) {
        return new ClinicalPreparationResponse(
                UUID.randomUUID(), patientId, attentionId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                "120/80", 72, new BigDecimal("36.5"), new BigDecimal("70.2"),
                "Paciente en buenas condiciones",
                List.of("Penicilina"), List.of("Ibuprofeno"),
                Instant.now(), Instant.now()
        );
    }

    private CurrentAttentionResponse sampleCurrentAttentionResponse(UUID patientId, UUID attentionId) {
        return new CurrentAttentionResponse(
                true,
                new CurrentAttentionResponse.PatientSummary(patientId, "PAC-001", "Juan Perez", LocalDate.of(1990, 1, 1)),
                new CurrentAttentionResponse.CurrentAttentionDetail(
                        attentionId, patientId,
                        new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Clinica"),
                        null, "Motivo", "Notas", "Siguientes pasos",
                        Instant.now(), Instant.now(), Instant.now(),
                        List.of(), List.of(), List.of(), null
                ),
                null
        );
    }
}
