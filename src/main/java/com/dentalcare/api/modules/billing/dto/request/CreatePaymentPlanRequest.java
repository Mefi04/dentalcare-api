package com.dentalcare.api.modules.billing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePaymentPlanRequest(
        @NotNull(message = "Installments count is required")
        @Min(value = 2, message = "Installments count must be between 2 and 60")
        @Max(value = 60, message = "Installments count must be between 2 and 60")
        Integer installmentsCount,
        @NotNull(message = "First due date is required")
        LocalDate firstDueDate) {
}
