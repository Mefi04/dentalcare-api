package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;

import java.time.Instant;
import java.util.UUID;

public record PatientTreatmentProcedureResponse(
        UUID id,
        Integer sequenceNumber,
        TreatmentProcedureStatus status,
        Instant performedAt,
        Instant completedAt) {
}
