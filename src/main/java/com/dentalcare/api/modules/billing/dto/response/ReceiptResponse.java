package com.dentalcare.api.modules.billing.dto.response;

import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        Long receiptNumber,
        UUID paymentId,
        UUID patientId,
        String concept,
        BigDecimal amount,
        PaymentMethod method,
        ReceiptStatus status,
        Instant issuedAt,
        UUID issuedByUserId) {
}
