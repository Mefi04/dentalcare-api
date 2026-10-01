package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalRecordService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Odontogram", description = "Patient dental odontogram charting, findings, and history")
public class OdontogramController {

    private final ClinicalRecordService clinicalRecordService;

    public OdontogramController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @PostMapping("/patients/{patientId}/odontogram/findings")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register an odontogram finding on a tooth")
    public ResponseEntity<OdontogramFindingResponse> createFinding(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateOdontogramFindingRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        OdontogramFindingResponse response = clinicalRecordService.createOdontogramFinding(
                patientId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/patients/{patientId}/odontogram")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get current odontogram chart for a patient and dentition")
    public ResponseEntity<OdontogramResponse> getCurrentOdontogram(
            @PathVariable UUID patientId,
            @RequestParam(required = false) DentitionType dentition) {
        return ResponseEntity.ok(clinicalRecordService.findCurrentOdontogram(patientId, dentition));
    }

    @GetMapping("/patients/{patientId}/odontogram/findings")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated odontogram findings history for a patient")
    public ResponseEntity<Page<OdontogramFindingResponse>> getFindingsHistory(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findOdontogramFindingsByPatient(patientId, page, size));
    }
}
