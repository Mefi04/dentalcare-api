package com.dentalcare.api.modules.prescriptions.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrescriptionRequest(
        @NotBlank @Size(max = 200) String medication,
        @NotBlank @Size(max = 150) String presentation,
        @NotBlank @Size(max = 150) String dosage,
        @NotBlank @Size(max = 150) String frequency,
        @NotBlank @Size(max = 150) String duration,
        @Size(max = 2000) String instructions
) {
}
