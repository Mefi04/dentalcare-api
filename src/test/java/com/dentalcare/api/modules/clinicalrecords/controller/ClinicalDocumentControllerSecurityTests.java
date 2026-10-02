package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicalDocumentController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ClinicalDocumentControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ClinicalDocumentService clinicalDocumentService;
    @MockitoBean private JwtService jwtService;

    @Test
    @DisplayName("Unauthenticated requests to clinical document endpoints return 401 Unauthorized")
    void unauthenticatedRequestsReturn401() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents", patientId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}", patientId, documentId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("User without clinical permissions returns 403 Forbidden")
    void userWithoutPermissionsReturns403() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("random-user", "OTHER_PERMISSION");

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer random-user"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}", patientId, documentId)
                .header("Authorization", "Bearer random-user"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer random-user")
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with CLINICAL_RECORD_READ can list and view details but cannot write")
    void userWithReadPermissionCanReadButCannotWrite() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        when(clinicalDocumentService.findDocumentsByPatient(eq(patientId), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(documentId, patientId))));
        when(clinicalDocumentService.findDocumentById(patientId, documentId))
                .thenReturn(sampleResponse(documentId, patientId));

        // Allowed reads
        mockMvc.perform(get("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId.toString()));

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()));

        // Denied writes (403 Forbidden)
        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer reader-token")
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with CLINICAL_RECORD_WRITE can create clinical documents")
    void userWithWritePermissionCanCreateDocument() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("writer-token", "CLINICAL_RECORD_WRITE");

        when(clinicalDocumentService.createDocument(eq(patientId), any(CreateClinicalDocumentRequest.class), any()))
                .thenReturn(sampleResponse(documentId, patientId));

        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer writer-token")
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.title").value("Radiografía Panorámica"));
    }

    @Test
    @DisplayName("Creating document with invalid body returns 400 Bad Request")
    void creatingDocumentWithInvalidBodyReturns400() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("writer-token", "CLINICAL_RECORD_WRITE");

        // Blank title
        String blankTitleJson = """
                {
                    "title": "   ",
                    "type": "RADIOGRAPHY"
                }
                """;
        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer writer-token")
                .contentType("application/json")
                .content(blankTitleJson))
                .andExpect(status().isBadRequest());

        // Null type
        String nullTypeJson = """
                {
                    "title": "Documento",
                    "type": null
                }
                """;
        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer writer-token")
                .contentType("application/json")
                .content(nullTypeJson))
                .andExpect(status().isBadRequest());

        // Title exceeding 150 characters
        String longTitleJson = """
                {
                    "title": "%s",
                    "type": "RADIOGRAPHY"
                }
                """.formatted("a".repeat(151));
        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer writer-token")
                .contentType("application/json")
                .content(longTitleJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Document response exposes safe file metadata and never exposes internal storageObjectKey")
    void documentResponseExposesSafeMetadataWithoutStorageObjectKey() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        ClinicalDocumentResponse responseWithFile = new ClinicalDocumentResponse(
                documentId,
                patientId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dr. Perez"),
                "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio",
                LocalDate.of(2026, 10, 1),
                "radiografia.pdf",
                2048L,
                "application/pdf",
                true,
                Instant.now(),
                Instant.now()
        );

        when(clinicalDocumentService.findDocumentById(patientId, documentId)).thenReturn(responseWithFile);

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.hasFile").value(true))
                .andExpect(jsonPath("$.fileName").value("radiografia.pdf"))
                .andExpect(jsonPath("$.fileSize").value(2048))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storageObjectKey").doesNotExist())
                .andExpect(jsonPath("$.storage_object_key").doesNotExist());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private String validDocumentBody() {
        return """
                {
                    "title": "Radiografía Panorámica",
                    "type": "RADIOGRAPHY",
                    "description": "Estudio radiológico de control",
                    "documentDate": "2026-10-01"
                }
                """;
    }

    private ClinicalDocumentResponse sampleResponse(UUID documentId, UUID patientId) {
        return new ClinicalDocumentResponse(
                documentId,
                patientId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dr. Perez"),
                "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio radiológico de control",
                LocalDate.of(2026, 10, 1),
                Instant.now(),
                Instant.now()
        );
    }
}
