package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalRecordService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Odontogram", description = "Patient dental odontogram charting, surface-level findings, and history")
public class OdontogramController {

    private final ClinicalRecordService clinicalRecordService;

    public OdontogramController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @PostMapping("/patients/{patientId}/odontogram/findings")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register an odontogram finding on a tooth or tooth surface")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Finding registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request, tooth code, surface, or mismatched attention"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires CLINICAL_RECORD_WRITE"),
            @ApiResponse(responseCode = "404", description = "Patient or clinical attention not found")
    })
    public ResponseEntity<OdontogramFindingResponse> createFinding(
            @Parameter(description = "Patient ID") @PathVariable UUID patientId,
            @Valid @RequestBody CreateOdontogramFindingRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        OdontogramFindingResponse response = clinicalRecordService.createOdontogramFinding(
                patientId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/patients/{patientId}/odontogram")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get current odontogram chart for a patient and dentition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current odontogram chart retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires CLINICAL_RECORD_READ"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<OdontogramResponse> getCurrentOdontogram(
            @Parameter(description = "Patient ID") @PathVariable UUID patientId,
            @Parameter(description = "Dentition type (defaults to ADULT if omitted)") @RequestParam(required = false) DentitionType dentition) {
        return ResponseEntity.ok(clinicalRecordService.findCurrentOdontogram(patientId, dentition));
    }

    @GetMapping("/patients/{patientId}/odontogram/findings")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated odontogram findings history for a patient with optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Odontogram findings history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires CLINICAL_RECORD_READ"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<Page<OdontogramFindingResponse>> getFindingsHistory(
            @Parameter(description = "Patient ID") @PathVariable UUID patientId,
            @Parameter(description = "Filter by FDI tooth code (optional)") @RequestParam(required = false) String toothCode,
            @Parameter(description = "Filter by dentition type (optional)") @RequestParam(required = false) DentitionType dentition,
            @Parameter(description = "Filter by finding type (optional)") @RequestParam(required = false) ToothFinding finding,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findOdontogramFindingsByPatient(
                patientId, toothCode, dentition, finding, page, size));
    }
}
