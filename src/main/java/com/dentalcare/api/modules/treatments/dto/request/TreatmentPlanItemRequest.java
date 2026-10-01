package com.dentalcare.api.modules.treatments.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TreatmentPlanItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 200, message = "Item name must not exceed 200 characters") String name,
        @Size(max = 20, message = "Tooth must not exceed 20 characters") String tooth,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero") Integer quantity,
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Unit price must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Unit price must have at most 10 integer digits and 2 decimals")
        BigDecimal unitPrice) {
}
