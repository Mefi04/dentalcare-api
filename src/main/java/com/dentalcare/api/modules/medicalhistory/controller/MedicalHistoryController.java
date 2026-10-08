package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.UpdateMedicalHistoryRequest;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryService;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/medical-history")
@Tag(name = "Medical history", description = "Clinical background associated with a patient")
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;
    private final MedicalHistoryWorkflowService workflowService;

    public MedicalHistoryController(MedicalHistoryService medicalHistoryService, MedicalHistoryWorkflowService workflowService) {
        this.medicalHistoryService = medicalHistoryService;
        this.workflowService = workflowService;
    }

    @Operation(summary = "Get a patient's medical history")
    @GetMapping
    @PreAuthorize("hasAuthority('MEDICAL_HISTORY_READ')")
    public ResponseEntity<MedicalHistoryResponse> findByPatientId(@PathVariable UUID patientId) {
        return ResponseEntity.ok(medicalHistoryService.findByPatientId(patientId));
    }

    @Operation(summary = "Import a dentist-attested legacy medical history", deprecated = true,
            description = "Compatibility endpoint. Creates an immutable LEGACY_COMPATIBILITY version; new clients must use questionnaires.")
    @PutMapping
    @PreAuthorize("hasAuthority('MEDICAL_HISTORY_LEGACY_IMPORT')")
    public ResponseEntity<MedicalHistoryResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID patientId,
            @Valid @RequestBody UpdateMedicalHistoryRequest request) {
        return ResponseEntity.ok(workflowService.importLegacy(patientId, request, principal.userId()));
    }
}
