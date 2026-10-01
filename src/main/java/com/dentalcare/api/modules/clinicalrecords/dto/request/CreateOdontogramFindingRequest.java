package com.dentalcare.api.modules.clinicalrecords.dto.request;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOdontogramFindingRequest(
        UUID attentionId,

        @NotNull(message = "Dentition type is required")
        DentitionType dentition,

        @NotBlank(message = "Tooth code is required")
        @Size(max = 10, message = "Tooth code cannot exceed 10 characters")
        String toothCode,

        ToothSurface surface,

        @NotNull(message = "Tooth finding is required")
        ToothFinding finding,

        @Size(max = 500, message = "Observation cannot exceed 500 characters")
        String observation) {
}
