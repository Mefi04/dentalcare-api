package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClinicalEvolutionResponse(
        UUID id,
        UUID patientId,
        UUID attentionId,
        ClinicalProfessionalResponse author,
        LocalDate consultationDate,
        String procedureSummary,
        String note,
        Instant createdAt) {
}
