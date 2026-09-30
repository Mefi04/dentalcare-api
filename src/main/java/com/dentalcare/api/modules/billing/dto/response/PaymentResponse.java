package com.dentalcare.api.modules.billing.dto.response;

import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID chargeId,
        PaymentKind kind,
        PaymentMethod method,
        BigDecimal amount,
        Instant createdAt) {
}
