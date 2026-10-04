package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.modules.billing.dto.request.CancelPaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.service.PaymentPlanService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
@Tag(name = "Payment plans", description = "Payment plans and derived installment status for a patient charge")
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;

    public PaymentPlanController(PaymentPlanService paymentPlanService) {
        this.paymentPlanService = paymentPlanService;
    }

    @Operation(summary = "Create a payment plan for a charge",
            description = "Installment amounts and the plan total are calculated from the charge pending balance.")
    @PostMapping("/charges/{chargeId}/payment-plan")
    @PreAuthorize("hasAuthority('BILLING_PLAN_MANAGE')")
    public ResponseEntity<PaymentPlanResponse> create(
            @PathVariable UUID patientId,
            @PathVariable UUID chargeId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreatePaymentPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentPlanService.create(patientId, chargeId, principal.userId(), request));
    }

    @Operation(summary = "Get the active payment plan of a charge")
    @GetMapping("/charges/{chargeId}/payment-plan")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<PaymentPlanResponse> findActive(
            @PathVariable UUID patientId,
            @PathVariable UUID chargeId) {
        return ResponseEntity.ok(paymentPlanService.findActiveByCharge(patientId, chargeId));
    }

    @Operation(summary = "List a patient's payment plans")
    @GetMapping("/payment-plans")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<List<PaymentPlanResponse>> list(@PathVariable UUID patientId) {
        return ResponseEntity.ok(paymentPlanService.list(patientId));
    }

    @Operation(summary = "Cancel a payment plan")
    @PostMapping("/payment-plans/{planId}/cancel")
    @PreAuthorize("hasAuthority('BILLING_PLAN_MANAGE')")
    public ResponseEntity<PaymentPlanResponse> cancel(
            @PathVariable UUID patientId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CancelPaymentPlanRequest request) {
        return ResponseEntity.ok(paymentPlanService.cancel(patientId, planId, principal.userId(), request));
    }
}
