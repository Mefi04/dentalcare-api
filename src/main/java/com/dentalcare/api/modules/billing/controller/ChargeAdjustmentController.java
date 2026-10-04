package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.modules.billing.dto.request.CreateChargeDiscountRequest;
import com.dentalcare.api.modules.billing.dto.request.VoidChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;
import com.dentalcare.api.modules.billing.service.ChargeAdjustmentService;
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
@Tag(name = "Billing adjustments", description = "Discounts and voids of a patient charge")
public class ChargeAdjustmentController {

    private final ChargeAdjustmentService chargeAdjustmentService;

    public ChargeAdjustmentController(ChargeAdjustmentService chargeAdjustmentService) {
        this.chargeAdjustmentService = chargeAdjustmentService;
    }

    @Operation(summary = "Discount a charge")
    @PostMapping("/charges/{chargeId}/discounts")
    @PreAuthorize("hasAuthority('BILLING_ADJUSTMENT_CREATE')")
    public ResponseEntity<ChargeAdjustmentResponse> discount(
            @PathVariable UUID patientId,
            @PathVariable UUID chargeId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateChargeDiscountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chargeAdjustmentService.discount(patientId, chargeId, principal.userId(), request));
    }

    @Operation(summary = "Void a charge")
    @PostMapping("/charges/{chargeId}/void")
    @PreAuthorize("hasAuthority('BILLING_ADJUSTMENT_CREATE')")
    public ResponseEntity<ChargeAdjustmentResponse> voidCharge(
            @PathVariable UUID patientId,
            @PathVariable UUID chargeId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody VoidChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chargeAdjustmentService.voidCharge(patientId, chargeId, principal.userId(), request));
    }

    @Operation(summary = "List charge adjustments")
    @GetMapping("/charge-adjustments")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public List<ChargeAdjustmentResponse> list(@PathVariable UUID patientId) {
        return chargeAdjustmentService.list(patientId);
    }
}
