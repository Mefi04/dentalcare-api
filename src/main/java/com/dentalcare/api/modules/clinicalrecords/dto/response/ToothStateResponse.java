package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Represents the derived clinical state and surface findings of a specific tooth")
public record ToothStateResponse(
        @Schema(description = "FDI two-digit tooth code (e.g. '11', '16', '55')", example = "11")
        String toothCode,

        @Schema(description = "FDI tooth number as integer", example = "11")
        Integer toothNumber,

        @Schema(description = "Whole-tooth finding if applicable (e.g. MISSING, IMPLANT, CROWN)", example = "MISSING")
        ToothFinding globalFinding,

        @Schema(description = "Map of anatomical surfaces to their current finding")
        Map<ToothSurface, ToothFinding> surfaces,

        @Schema(description = "Timestamp when this tooth was last updated by a finding")
        Instant lastUpdatedAt
) {
}
