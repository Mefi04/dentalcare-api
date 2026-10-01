package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;

import java.time.Instant;
import java.util.UUID;

public record TreatmentProcedureResponse(
        UUID id,
        UUID treatmentPlanId,
        UUID treatmentPlanItemId,
        UUID patientId,
        TreatmentPlanProfessionalResponse professional,
        String procedureName,
        String tooth,
        Integer sequenceNumber,
        String clinicalObservations,
        String completionNotes,
        TreatmentProcedureStatus status,
        Instant performedAt,
        Instant completedAt) {
}
