package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.service.PatientTreatmentPlanService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/treatment-plans")
@PreAuthorize("hasRole('PATIENT')")
public class PatientTreatmentPlanController {
    private final PatientTreatmentPlanService service;

    public PatientTreatmentPlanController(PatientTreatmentPlanService service) {
        this.service = service;
    }

    @Operation(summary = "List approved treatment plans owned by the authenticated patient")
    @GetMapping
    public Page<PatientTreatmentPlanResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findMine(principal.userId(), page, size);
    }

    @Operation(summary = "Get one approved treatment plan owned by the authenticated patient")
    @GetMapping("/{planId}")
    public PatientTreatmentPlanResponse detail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID planId) {
        return service.findMineById(principal.userId(), planId);
    }
}
