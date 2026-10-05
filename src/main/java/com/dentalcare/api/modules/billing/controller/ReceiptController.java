package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.service.ReceiptService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
@Tag(name = "Billing receipts", description = "Persistent receipts issued for a patient payment")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Operation(summary = "Issue the receipt of a payment",
            description = "Creates the single receipt of an existing payment. Number, amount and concept come from the payment and the database sequence.")
    @PostMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAuthority('BILLING_RECEIPT_CREATE')")
    public ResponseEntity<ReceiptResponse> issue(
            @PathVariable UUID patientId,
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.issue(patientId, paymentId, principal.userId()));
    }

    @Operation(summary = "Get the receipt of a payment")
    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<ReceiptResponse> find(
            @PathVariable UUID patientId,
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(receiptService.findByPayment(patientId, paymentId));
    }
}
