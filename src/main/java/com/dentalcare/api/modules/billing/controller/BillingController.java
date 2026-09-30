package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
@Tag(name = "Billing", description = "Patient account statement, charges and payments")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "Get a patient's account statement",
            description = "Returns charges, payments and a balance derived from persisted movements.")
    @GetMapping("/account-statement")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<AccountStatementResponse> findAccountStatement(@PathVariable UUID patientId) {
        return ResponseEntity.ok(billingService.findAccountStatement(patientId));
    }

    @Operation(summary = "Register a charge on a patient's account")
    @PostMapping("/charges")
    @PreAuthorize("hasAuthority('BILLING_CHARGE_CREATE')")
    public ResponseEntity<ChargeResponse> createCharge(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createCharge(patientId, request));
    }

    @Operation(summary = "Register a payment or an advance on a patient's account",
            description = "With chargeId the amount is applied to that charge and must not exceed its pending "
                    + "balance; without chargeId it is registered as an advance.")
    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('BILLING_PAYMENT_CREATE')")
    public ResponseEntity<PaymentResponse> registerPayment(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.registerPayment(patientId, request));
    }
}
