package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Historical odontogram finding record")
public record OdontogramFindingResponse(
        @Schema(description = "Finding record ID")
        UUID id,

        @Schema(description = "Patient ID")
        UUID patientId,

        @Schema(description = "Clinical attention ID if registered during an encounter")
        UUID attentionId,

        @Schema(description = "Professional who recorded the finding")
        ClinicalProfessionalResponse author,

        @Schema(description = "Dentition type", example = "ADULT")
        DentitionType dentition,

        @Schema(description = "FDI tooth code", example = "16")
        String toothCode,

        @Schema(description = "Tooth surface if surface-level finding", example = "OCCLUSAL")
        ToothSurface surface,

        @Schema(description = "Clinical finding", example = "CARIOUS")
        ToothFinding finding,

        @Schema(description = "Clinical observation")
        String observation,

        @Schema(description = "Timestamp when finding was recorded")
        Instant createdAt) {
}
