package com.dentalcare.api.modules.billing.dto.response;

import com.dentalcare.api.modules.billing.model.CashMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        UUID cashShiftId,
        CashMovementType type,
        BigDecimal amount,
        String concept,
        UUID createdByUserId,
        Instant createdAt) {
}
