package com.dentalcare.api.modules.billing.dto.response;

import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargeAdjustmentResponse(
        UUID id,
        UUID chargeId,
        UUID patientId,
        ChargeAdjustmentType type,
        BigDecimal amount,
        String reason,
        UUID requestedByUserId,
        UUID authorizedByUserId,
        Instant createdAt) {
}
