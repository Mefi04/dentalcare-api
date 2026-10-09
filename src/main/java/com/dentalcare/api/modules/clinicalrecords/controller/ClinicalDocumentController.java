package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UploadClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UpdateClinicalDocumentVisibilityRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentService;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentFileValidator;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clinical documents", description = "Patient clinical documents metadata management and storage")
public class ClinicalDocumentController {

    private final ClinicalDocumentService clinicalDocumentService;

    public ClinicalDocumentController(ClinicalDocumentService clinicalDocumentService) {
        this.clinicalDocumentService = clinicalDocumentService;
    }

    @PostMapping("/patients/{patientId}/documents")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register clinical document metadata for a patient")
    public ResponseEntity<ClinicalDocumentResponse> createDocument(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateClinicalDocumentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalDocumentResponse response = clinicalDocumentService.createDocument(
                patientId, request, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/patients/{patientId}/documents/{id}")
                .buildAndExpand(patientId, response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(value = "/patients/{patientId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Upload clinical document file and register metadata for a patient")
    public ResponseEntity<ClinicalDocumentResponse> uploadDocument(
            @PathVariable UUID patientId,
            @Valid @ModelAttribute UploadClinicalDocumentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalDocumentResponse response = clinicalDocumentService.uploadDocument(
                patientId, request, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/patients/{patientId}/documents/{id}")
                .buildAndExpand(patientId, response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/patients/{patientId}/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Download clinical document file by patient and document id")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId) {
        ClinicalDocumentDownload download = clinicalDocumentService.downloadDocument(patientId, documentId);

        String cleanFileName = download.fileName() != null && !download.fileName().isBlank()
                ? download.fileName().replaceAll("[\\r\\n]", "").trim()
                : "document";

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(cleanFileName, StandardCharsets.UTF_8)
                .build();

        MediaType mediaType;
        try {
            mediaType = ClinicalDocumentFileValidator.ALLOWED_MIME_TYPES.contains(download.contentType())
                    ? MediaType.parseMediaType(download.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

        if (download.contentLength() > 0) {
            responseBuilder.contentLength(download.contentLength());
        }

        return responseBuilder.body(new InputStreamResource(download.inputStream()));
    }

    @GetMapping("/patients/{patientId}/documents")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated clinical documents for a patient")
    public ResponseEntity<Page<ClinicalDocumentResponse>> getDocuments(
            @PathVariable UUID patientId,
            @RequestParam(required = false) ClinicalDocumentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalDocumentService.findDocumentsByPatient(patientId, type, page, size));
    }

    @GetMapping("/patients/{patientId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get clinical document details by patient and document id")
    public ResponseEntity<ClinicalDocumentResponse> getDocumentById(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(clinicalDocumentService.findDocumentById(patientId, documentId));
    }

    @PatchMapping("/patients/{patientId}/documents/{documentId}/visibility")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Share or unshare a clinical document with the patient")
    public ResponseEntity<ClinicalDocumentResponse> updatePatientVisibility(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId,
            @Valid @RequestBody UpdateClinicalDocumentVisibilityRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(clinicalDocumentService.updatePatientVisibility(
                patientId, documentId, request.visible(), principal.userId()));
    }
}
