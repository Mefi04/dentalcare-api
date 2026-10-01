package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clinical records", description = "Patient clinical record consolidated summary and history")
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;

    public ClinicalRecordController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @GetMapping("/patients/{patientId}/clinical-record")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get patient clinical record consolidated summary")
    public ResponseEntity<ClinicalRecordSummaryResponse> getSummary(@PathVariable UUID patientId) {
        return ResponseEntity.ok(clinicalRecordService.findSummaryByPatient(patientId));
    }

    @GetMapping("/patients/{patientId}/clinical-history")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get patient chronological clinical history entries")
    public ResponseEntity<Page<ClinicalHistoryEntryResponse>> getHistory(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findClinicalHistory(patientId, page, size));
    }
}
