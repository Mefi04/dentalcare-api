package com.dentalcare.api.modules.clinicalrecords.dto.request;

import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateClinicalDiagnosisRequest(
        @NotNull(message = "Diagnosis type is required")
        DiagnosisType type,

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        UUID treatmentPlanId) {
}
