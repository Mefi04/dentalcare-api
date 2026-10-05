package com.dentalcare.api.modules.billing.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OpenCashShiftRequest(
        @NotNull(message = "Opening amount is required")
        @PositiveOrZero(message = "Opening amount must be greater than or equal to zero")
        @Digits(integer = 10, fraction = 2, message = "Opening amount must have at most 10 integer digits and 2 decimals")
        BigDecimal openingAmount,
        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes) {
}
