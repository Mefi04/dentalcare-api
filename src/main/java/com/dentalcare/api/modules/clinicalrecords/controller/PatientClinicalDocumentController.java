package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.PatientClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentService;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentFileValidator;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/documents")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient clinical documents", description = "Shared clinical documents for the authenticated patient")
public class PatientClinicalDocumentController {

    private final ClinicalDocumentService clinicalDocumentService;

    public PatientClinicalDocumentController(ClinicalDocumentService clinicalDocumentService) {
        this.clinicalDocumentService = clinicalDocumentService;
    }

    @GetMapping
    @Operation(summary = "List clinical documents explicitly shared with the authenticated patient")
    public ResponseEntity<Page<PatientClinicalDocumentResponse>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) ClinicalDocumentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalDocumentService.findVisibleDocumentsForPatient(
                principal.userId(), type, page, size));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get one clinical document shared with the authenticated patient")
    public ResponseEntity<PatientClinicalDocumentResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(clinicalDocumentService.findVisibleDocumentForPatient(
                principal.userId(), documentId));
    }

    @GetMapping("/{documentId}/download")
    @Operation(summary = "Download one clinical document shared with the authenticated patient")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID documentId) {
        ClinicalDocumentDownload download = clinicalDocumentService.downloadVisibleDocumentForPatient(
                principal.userId(), documentId);
        String cleanFileName = download.fileName() != null && !download.fileName().isBlank()
                ? download.fileName().replaceAll("[\\r\\n]", "").trim() : "document";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(cleanFileName, StandardCharsets.UTF_8)
                .build();
        MediaType mediaType;
        try {
            mediaType = ClinicalDocumentFileValidator.ALLOWED_MIME_TYPES.contains(download.contentType())
                    ? MediaType.parseMediaType(download.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        if (download.contentLength() > 0) {
            response.contentLength(download.contentLength());
        }
        return response.body(new InputStreamResource(download.inputStream()));
    }
}
