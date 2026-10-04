package com.dentalcare.api.modules.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPaymentPlanRequest(
        @NotBlank(message = "Cancel reason is required")
        @Size(max = 500, message = "Cancel reason must not exceed 500 characters")
        String reason) {
}
