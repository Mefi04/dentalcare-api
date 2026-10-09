package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UploadClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.PatientClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentService;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageDisabledException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ClinicalDocumentController.class, PatientClinicalDocumentController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
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

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF".getBytes());
        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "Radiografia")
                .param("type", "RADIOGRAPHY"))
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

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId)
                .header("Authorization", "Bearer random-user"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer random-user")
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF".getBytes());
        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "Radiografia")
                .param("type", "RADIOGRAPHY")
                .header("Authorization", "Bearer random-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with CLINICAL_RECORD_READ can list, view, and download but cannot write or upload")
    void userWithReadPermissionCanReadAndDownloadButCannotWriteOrUpload() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        when(clinicalDocumentService.findDocumentsByPatient(eq(patientId), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(documentId, patientId))));
        when(clinicalDocumentService.findDocumentById(patientId, documentId))
                .thenReturn(sampleResponse(documentId, patientId));

        ByteArrayInputStream stream = new ByteArrayInputStream("PDF_STREAM_CONTENT".getBytes(StandardCharsets.UTF_8));
        ClinicalDocumentDownload download = new ClinicalDocumentDownload(
                stream, "radiografia.pdf", "application/pdf", 18L);
        when(clinicalDocumentService.downloadDocument(patientId, documentId)).thenReturn(download);

        // Allowed reads
        mockMvc.perform(get("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId.toString()));

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()));

        // Allowed download
        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Length", "18"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().string("PDF_STREAM_CONTENT"));

        // Denied write metadata (403 Forbidden)
        mockMvc.perform(post("/api/v1/patients/{patientId}/documents", patientId)
                .header("Authorization", "Bearer reader-token")
                .contentType("application/json")
                .content(validDocumentBody()))
                .andExpect(status().isForbidden());

        // Denied upload (403 Forbidden)
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF".getBytes());
        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "Radiografia")
                .param("type", "RADIOGRAPHY")
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with CLINICAL_RECORD_WRITE can create clinical documents and upload files")
    void userWithWritePermissionCanCreateDocumentAndUpload() throws Exception {
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

        // Test multipart upload
        ClinicalDocumentResponse uploadedResponse = new ClinicalDocumentResponse(
                documentId,
                patientId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dr. Perez"),
                "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio",
                LocalDate.of(2026, 10, 1),
                "radiografia.pdf",
                1024L,
                "application/pdf",
                true,
                Instant.now(),
                Instant.now()
        );
        when(clinicalDocumentService.uploadDocument(eq(patientId), any(UploadClinicalDocumentRequest.class), any()))
                .thenReturn(uploadedResponse);

        MockMultipartFile file = new MockMultipartFile(
                "file", "radiografia.pdf", "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "Radiografía Panorámica")
                .param("type", "RADIOGRAPHY")
                .param("description", "Estudio")
                .param("documentDate", "2026-10-01")
                .header("Authorization", "Bearer writer-token"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.hasFile").value(true))
                .andExpect(jsonPath("$.fileName").value("radiografia.pdf"));
    }

    @Test
    @DisplayName("Upload with invalid parameters returns 400 Bad Request")
    void uploadWithInvalidParametersReturns400() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("writer-token", "CLINICAL_RECORD_WRITE");

        MockMultipartFile file = new MockMultipartFile(
                "file", "radiografia.pdf", "application/pdf", "%PDF".getBytes());

        // Blank title
        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "   ")
                .param("type", "RADIOGRAPHY")
                .header("Authorization", "Bearer writer-token"))
                .andExpect(status().isBadRequest());

        // Missing type
        mockMvc.perform(multipart("/api/v1/patients/{patientId}/documents/upload", patientId)
                .file(file)
                .param("title", "Titulo")
                .header("Authorization", "Bearer writer-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Downloading document without attached file (legacy) returns 404 Not Found")
    void downloadLegacyDocumentWithoutFileReturns404() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        when(clinicalDocumentService.downloadDocument(patientId, documentId))
                .thenThrow(new ResourceNotFoundException("Clinical document does not have an attached file"));

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Clinical document does not have an attached file"));
    }

    @Test
    @DisplayName("Downloading document when storage object is missing returns 404 Not Found")
    void downloadMissingStorageObjectReturns404() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        when(clinicalDocumentService.downloadDocument(patientId, documentId))
                .thenThrow(new DocumentNotFoundInStorageException("Document not found in storage", null));

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document not found in storage"));
    }

    @Test
    @DisplayName("Downloading document when R2 storage is disabled returns 503 Service Unavailable")
    void downloadWhenStorageDisabledReturns503() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader-token", "CLINICAL_RECORD_READ");

        when(clinicalDocumentService.downloadDocument(patientId, documentId))
                .thenThrow(new DocumentStorageDisabledException("Clinical document storage is disabled by configuration"));

        mockMvc.perform(get("/api/v1/patients/{patientId}/documents/{documentId}/download", patientId, documentId)
                .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Clinical document storage is disabled by configuration"));
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

    @Test
    @DisplayName("Only clinical writers can change patient visibility")
    void visibilityUpdateRequiresClinicalWritePermission() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("reader", "CLINICAL_RECORD_READ");
        token("writer", "CLINICAL_RECORD_WRITE");
        when(clinicalDocumentService.updatePatientVisibility(
                eq(patientId), eq(documentId), eq(true), any()))
                .thenReturn(sampleResponse(documentId, patientId));

        mockMvc.perform(patch("/api/v1/patients/{patientId}/documents/{documentId}/visibility",
                        patientId, documentId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json")
                .content("{\"visible\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/patients/{patientId}/documents/{documentId}/visibility",
                        patientId, documentId)
                .header("Authorization", "Bearer writer")
                .contentType("application/json")
                .content("{\"visible\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Patient role can list detail and download only through self-service endpoints")
    void patientCanUseOwnedSharedDocumentEndpoints() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        token("patient-token", userId, "ROLE_PATIENT");
        PatientClinicalDocumentResponse response = new PatientClinicalDocumentResponse(
                documentId,
                new ClinicalProfessionalResponse(UUID.randomUUID(), "Dra. Ana"),
                "Radiografía",
                ClinicalDocumentType.RADIOGRAPHY,
                "Control",
                LocalDate.of(2026, 10, 2),
                "radiografia.pdf",
                6L,
                "application/pdf",
                true,
                Instant.now(),
                Instant.now(),
                Instant.now());
        when(clinicalDocumentService.findVisibleDocumentsForPatient(eq(userId), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(clinicalDocumentService.findVisibleDocumentForPatient(eq(userId), eq(documentId)))
                .thenReturn(response);
        when(clinicalDocumentService.downloadVisibleDocumentForPatient(eq(userId), eq(documentId)))
                .thenReturn(new ClinicalDocumentDownload(
                        new ByteArrayInputStream("shared".getBytes(StandardCharsets.UTF_8)),
                        "radiografia.pdf", "application/pdf", 6L));

        mockMvc.perform(get("/api/v1/patients/me/documents")
                .param("patientId", UUID.randomUUID().toString())
                .param("userId", UUID.randomUUID().toString())
                .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId.toString()))
                .andExpect(jsonPath("$.content[0].patientId").doesNotExist())
                .andExpect(jsonPath("$.content[0].storageObjectKey").doesNotExist());

        mockMvc.perform(get("/api/v1/patients/me/documents/{documentId}", documentId)
                .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()));

        mockMvc.perform(get("/api/v1/patients/me/documents/{documentId}/download", documentId)
                .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("shared"));

        verify(clinicalDocumentService).findVisibleDocumentsForPatient(eq(userId), any(), eq(0), eq(20));
        verify(clinicalDocumentService).findVisibleDocumentForPatient(userId, documentId);
        verify(clinicalDocumentService).downloadVisibleDocumentForPatient(userId, documentId);
    }

    @Test
    @DisplayName("Staff role without PATIENT cannot use patient document self-service")
    void staffCannotUsePatientDocumentSelfService() throws Exception {
        token("staff-token", "CLINICAL_RECORD_READ");
        mockMvc.perform(get("/api/v1/patients/me/documents")
                .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    private void token(String token, String... authorities) {
        token(token, UUID.randomUUID(), authorities);
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
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
