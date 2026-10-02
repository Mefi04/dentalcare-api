package com.dentalcare.api.modules.clinicalrecords.dto.request;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Request payload to record an odontogram finding on a tooth or tooth surface")
public record CreateOdontogramFindingRequest(
        @Schema(description = "Optional clinical attention ID to link this finding with an ongoing encounter")
        UUID attentionId,

        @NotNull(message = "Dentition type is required")
        @Schema(description = "Dentition classification (ADULT, CHILD, MIXED)", example = "ADULT", requiredMode = Schema.RequiredMode.REQUIRED)
        DentitionType dentition,

        @NotBlank(message = "Tooth code is required")
        @Size(max = 10, message = "Tooth code cannot exceed 10 characters")
        @Schema(description = "FDI two-digit tooth code (e.g. '11', '16', '55')", example = "16", requiredMode = Schema.RequiredMode.REQUIRED)
        String toothCode,

        @Schema(description = "Anatomical surface of the tooth (null for whole-tooth findings)", example = "OCCLUSAL")
        ToothSurface surface,

        @NotNull(message = "Tooth finding is required")
        @Schema(description = "Clinical finding type", example = "CARIOUS", requiredMode = Schema.RequiredMode.REQUIRED)
        ToothFinding finding,

        @Size(max = 500, message = "Observation cannot exceed 500 characters")
        @Schema(description = "Optional clinical observation or detail", example = "Caries oclusal profunda")
        String observation) {
}
