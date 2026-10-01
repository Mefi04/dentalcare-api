package com.dentalcare.api.modules.clinicalrecords.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateClinicalAttentionRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = 200, message = "Reason cannot exceed 200 characters")
        String reason,

        @NotBlank(message = "Clinical notes are required")
        @Size(max = 4000, message = "Clinical notes cannot exceed 4000 characters")
        String clinicalNotes,

        @Size(max = 1000, message = "Next steps cannot exceed 1000 characters")
        String nextSteps,

        UUID appointmentId) {
}
