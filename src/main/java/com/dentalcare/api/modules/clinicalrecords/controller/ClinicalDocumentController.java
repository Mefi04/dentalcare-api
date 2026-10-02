package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clinical documents", description = "Patient clinical documents metadata management")
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
}
