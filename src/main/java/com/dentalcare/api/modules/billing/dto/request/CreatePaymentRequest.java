package com.dentalcare.api.modules.billing.dto.request;

import com.dentalcare.api.modules.billing.model.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A payment applied to {@code chargeId}, or an advance when {@code chargeId} is omitted.
 * The payment kind is always derived by the backend.
 */
public record CreatePaymentRequest(
        UUID chargeId,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 integer digits and 2 decimals")
        BigDecimal amount,
        @NotNull(message = "Payment method is required")
        PaymentMethod method) {
}
