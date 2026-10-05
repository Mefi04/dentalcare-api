package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.billing.dto.request.CreateRefundRequest;
import com.dentalcare.api.modules.billing.dto.response.RefundResponse;
import com.dentalcare.api.modules.billing.service.RefundResult;
import com.dentalcare.api.modules.billing.service.RefundService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
@Tag(name = "Billing refunds", description = "Refunds of a patient payment")
public class RefundController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @Operation(summary = "Refund a payment")
    @PostMapping("/payments/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('BILLING_REFUND_CREATE')")
    public ResponseEntity<RefundResponse> create(
            @PathVariable UUID patientId,
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new BadRequestException(
                    "Idempotency key must not be blank and must not exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        RefundResult result = refundService.create(patientId, paymentId, principal.userId(), request, idempotencyKey);
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @Operation(summary = "List refunds")
    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public List<RefundResponse> list(@PathVariable UUID patientId) {
        return refundService.list(patientId);
    }
}
