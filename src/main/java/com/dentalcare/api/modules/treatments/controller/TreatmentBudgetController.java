package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.service.TreatmentBudgetService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Treatment budgets", description = "Immutable budgets generated from approved treatment plans")
public class TreatmentBudgetController {
    private final TreatmentBudgetService service;

    public TreatmentBudgetController(TreatmentBudgetService service) {
        this.service = service;
    }

    @PostMapping("/treatment-plans/{planId}/budgets")
    @PreAuthorize("hasAuthority('TREATMENT_BUDGET_CREATE')")
    @Operation(summary = "Generate a budget snapshot from an approved treatment plan")
    public ResponseEntity<TreatmentBudgetResponse> generate(
            @PathVariable UUID planId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TreatmentBudgetResponse response = service.generate(planId, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/treatment-budgets/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/treatment-plans/{planId}/budgets")
    @PreAuthorize("hasAuthority('TREATMENT_BUDGET_READ')")
    @Operation(summary = "List all budget versions for a treatment plan")
    public ResponseEntity<List<TreatmentBudgetResponse>> findByPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(service.findByPlan(planId));
    }

    @GetMapping("/treatment-budgets/{budgetId}")
    @PreAuthorize("hasAuthority('TREATMENT_BUDGET_READ')")
    @Operation(summary = "Get a treatment budget and its immutable item snapshot")
    public ResponseEntity<TreatmentBudgetResponse> findById(@PathVariable UUID budgetId) {
        return ResponseEntity.ok(service.findById(budgetId));
    }

    @PatchMapping("/treatment-budgets/{budgetId}/approve")
    @PreAuthorize("hasAuthority('TREATMENT_BUDGET_DECIDE')")
    @Operation(summary = "Approve a pending treatment budget")
    public ResponseEntity<TreatmentBudgetResponse> approve(
            @PathVariable UUID budgetId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.approve(budgetId, principal.userId()));
    }

    @PatchMapping("/treatment-budgets/{budgetId}/reject")
    @PreAuthorize("hasAuthority('TREATMENT_BUDGET_DECIDE')")
    @Operation(summary = "Reject a pending treatment budget")
    public ResponseEntity<TreatmentBudgetResponse> reject(
            @PathVariable UUID budgetId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.reject(budgetId, principal.userId()));
    }
}
