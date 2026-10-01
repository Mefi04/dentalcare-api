package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.request.UpdateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.service.TreatmentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Treatment plans", description = "Persistent treatment plan management")
public class TreatmentPlanController {

    private final TreatmentPlanService treatmentPlanService;

    public TreatmentPlanController(TreatmentPlanService treatmentPlanService) {
        this.treatmentPlanService = treatmentPlanService;
    }

    @GetMapping("/patients/{patientId}/treatment-plans")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_READ')")
    @Operation(summary = "List a patient's treatment plans")
    public ResponseEntity<Page<TreatmentPlanResponse>> findByPatient(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(treatmentPlanService.findByPatient(patientId, page, size));
    }

    @PostMapping("/patients/{patientId}/treatment-plans")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_CREATE')")
    @Operation(summary = "Create a draft treatment plan")
    public ResponseEntity<TreatmentPlanResponse> create(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateTreatmentPlanRequest request) {
        TreatmentPlanResponse response = treatmentPlanService.create(patientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/treatment-plans/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/treatment-plans/professionals")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_READ')")
    @Operation(summary = "List active dentists available for treatment plans")
    public ResponseEntity<List<TreatmentPlanProfessionalResponse>> findProfessionals() {
        return ResponseEntity.ok(treatmentPlanService.findProfessionals());
    }

    @GetMapping("/treatment-plans/{planId}")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_READ')")
    @Operation(summary = "Get a treatment plan")
    public ResponseEntity<TreatmentPlanResponse> findById(@PathVariable UUID planId) {
        return ResponseEntity.ok(treatmentPlanService.findById(planId));
    }

    @PutMapping("/treatment-plans/{planId}")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_UPDATE')")
    @Operation(summary = "Replace the editable fields of a draft treatment plan")
    public ResponseEntity<TreatmentPlanResponse> update(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateTreatmentPlanRequest request) {
        return ResponseEntity.ok(treatmentPlanService.update(planId, request));
    }

    @PatchMapping("/treatment-plans/{planId}/approve")
    @PreAuthorize("hasAuthority('TREATMENT_PLAN_APPROVE')")
    @Operation(summary = "Approve a draft treatment plan")
    public ResponseEntity<TreatmentPlanResponse> approve(@PathVariable UUID planId) {
        return ResponseEntity.ok(treatmentPlanService.approve(planId));
    }
}
