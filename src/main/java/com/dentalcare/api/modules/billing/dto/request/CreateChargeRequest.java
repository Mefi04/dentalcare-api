package com.dentalcare.api.modules.billing.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateChargeRequest(
        @NotBlank(message = "Concept is required")
        @Size(max = 200, message = "Concept must not exceed 200 characters")
        String concept,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 integer digits and 2 decimals")
        BigDecimal amount) {
}
