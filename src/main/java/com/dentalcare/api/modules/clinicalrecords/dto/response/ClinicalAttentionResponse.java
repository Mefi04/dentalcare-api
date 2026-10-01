package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ClinicalAttentionResponse(
        UUID id,
        UUID patientId,
        ClinicalProfessionalResponse professional,
        UUID appointmentId,
        String reason,
        String clinicalNotes,
        String nextSteps,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {
}
