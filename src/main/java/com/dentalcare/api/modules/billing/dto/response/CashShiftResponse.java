package com.dentalcare.api.modules.billing.dto.response;

import com.dentalcare.api.modules.billing.model.CashShiftStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashShiftResponse(
        UUID id,
        UUID userId,
        CashShiftStatus status,
        BigDecimal openingAmount,
        Instant openedAt,
        Instant closedAt,
        BigDecimal expectedAmount,
        BigDecimal countedAmount,
        BigDecimal difference,
        String openingNotes,
        String closingNotes) {
}
