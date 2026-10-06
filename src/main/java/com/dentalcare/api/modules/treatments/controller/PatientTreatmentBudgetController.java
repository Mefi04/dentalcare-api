package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.service.PatientTreatmentBudgetService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/treatment-budgets")
@PreAuthorize("hasRole('PATIENT')")
public class PatientTreatmentBudgetController {
    private final PatientTreatmentBudgetService service;

    public PatientTreatmentBudgetController(PatientTreatmentBudgetService service) { this.service = service; }

    @Operation(summary = "List clinic-approved treatment budgets owned by the authenticated patient")
    @GetMapping
    public Page<PatientTreatmentBudgetResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return service.findMine(principal.userId(), page, size);
    }

    @Operation(summary = "Get one clinic-approved treatment budget owned by the authenticated patient")
    @GetMapping("/{budgetId}")
    public PatientTreatmentBudgetResponse detail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID budgetId) {
        return service.findMineById(principal.userId(), budgetId);
    }

    @Operation(summary = "Accept an owned treatment budget as the authenticated patient")
    @PatchMapping("/{budgetId}/accept")
    public PatientTreatmentBudgetResponse accept(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID budgetId) {
        return service.accept(principal.userId(), budgetId);
    }

    @Operation(summary = "Reject an owned treatment budget as the authenticated patient")
    @PatchMapping("/{budgetId}/reject")
    public PatientTreatmentBudgetResponse reject(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID budgetId) {
        return service.reject(principal.userId(), budgetId);
    }
}
