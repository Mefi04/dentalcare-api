package com.dentalcare.api.modules.billing.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseCashShiftRequest(
        @NotNull(message = "Counted amount is required")
        @PositiveOrZero(message = "Counted amount must be greater than or equal to zero")
        @Digits(integer = 10, fraction = 2, message = "Counted amount must have at most 10 integer digits and 2 decimals")
        BigDecimal countedAmount,
        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes) {
}
