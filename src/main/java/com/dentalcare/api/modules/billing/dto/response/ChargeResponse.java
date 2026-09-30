package com.dentalcare.api.modules.billing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargeResponse(
        UUID id,
        String concept,
        BigDecimal amount,
        BigDecimal paid,
        BigDecimal pending,
        ChargeStatus status,
        Instant createdAt) {
}
