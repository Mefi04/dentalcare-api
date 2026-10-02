package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Patient odontogram chart representation with tooth and surface findings")
public record OdontogramResponse(
        @Schema(description = "Patient ID")
        UUID patientId,

        @Schema(description = "Dentition type (ADULT, CHILD, MIXED)", example = "ADULT")
        DentitionType dentition,

        @Schema(description = "Detailed list of teeth and their surface-level findings")
        List<ToothStateResponse> teeth,

        @Schema(description = "Summary map of toothCode to primary finding for backward compatibility")
        Map<String, ToothFinding> teethSummary,

        @Schema(description = "Chronological list of recent findings for this dentition")
        List<OdontogramFindingResponse> recentFindings
) {
    public OdontogramResponse(DentitionType dentition, Map<String, ToothFinding> teethSummary, List<OdontogramFindingResponse> recentFindings) {
        this(null, dentition, List.of(), teethSummary, recentFindings);
    }
}
