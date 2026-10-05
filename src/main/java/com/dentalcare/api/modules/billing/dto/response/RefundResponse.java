package com.dentalcare.api.modules.billing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID patientId,
        BigDecimal amount,
        String reason,
        UUID requestedByUserId,
        UUID authorizedByUserId,
        UUID cashShiftId,
        Instant createdAt) {
}
