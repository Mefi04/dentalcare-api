package com.dentalcare.api.modules.treatments.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTreatmentProcedureRequest(
        @NotNull(message = "Treatment plan item id is required") UUID treatmentPlanItemId,
        @Size(max = 4000, message = "Clinical observations must not exceed 4000 characters")
        String clinicalObservations) {
}
