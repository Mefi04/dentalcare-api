package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;

import java.time.Instant;
import java.util.UUID;

public record ClinicalDiagnosisResponse(
        UUID id,
        UUID patientId,
        UUID attentionId,
        UUID treatmentPlanId,
        ClinicalProfessionalResponse author,
        DiagnosisType type,
        String description,
        Instant createdAt) {
}
