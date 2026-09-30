package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.service.BillingService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/me/account-statement")
@Tag(name = "Patient billing", description = "Authenticated patient account statement self-service")
@PreAuthorize("hasRole('PATIENT')")
public class PatientBillingController {

    private final BillingService billingService;

    public PatientBillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "Get the authenticated patient's account statement",
            description = "Resolves identity strictly from the JWT principal. Requires ROLE_PATIENT.")
    @GetMapping
    public ResponseEntity<AccountStatementResponse> findCurrentPatientAccountStatement(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(billingService.findAccountStatementForAuthenticatedPatient(principal.userId()));
    }
}
