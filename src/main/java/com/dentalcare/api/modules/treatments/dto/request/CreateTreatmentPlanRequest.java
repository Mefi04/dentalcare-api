package com.dentalcare.api.modules.treatments.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTreatmentPlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters") String name,
        @Size(max = 4000, message = "Observations must not exceed 4000 characters") String observations,
        @NotNull(message = "Professional id is required") UUID professionalId,
        @NotNull(message = "Items are required")
        @Size(min = 1, max = 100, message = "Items must contain between 1 and 100 entries")
        List<@Valid TreatmentPlanItemRequest> items) {
}
