package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.UpdateMedicalHistoryRequest;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public MedicalHistoryController(MedicalHistoryService medicalHistoryService) {
        this.medicalHistoryService = medicalHistoryService;
    }

    @Operation(summary = "Get a patient's medical history")
    @GetMapping
    @PreAuthorize("hasAuthority('MEDICAL_HISTORY_READ')")
    public ResponseEntity<MedicalHistoryResponse> findByPatientId(@PathVariable UUID patientId) {
        return ResponseEntity.ok(medicalHistoryService.findByPatientId(patientId));
    }

    @Operation(summary = "Create or replace a patient's medical history")
    @PutMapping
    @PreAuthorize("hasAuthority('MEDICAL_HISTORY_UPDATE')")
    public ResponseEntity<MedicalHistoryResponse> update(
            @PathVariable UUID patientId,
            @Valid @RequestBody UpdateMedicalHistoryRequest request) {
        return ResponseEntity.ok(medicalHistoryService.update(patientId, request));
    }
}
