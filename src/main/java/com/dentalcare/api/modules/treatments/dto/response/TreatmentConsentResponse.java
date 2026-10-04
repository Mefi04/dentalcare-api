package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentConsentStatus;

import java.time.Instant;
import java.util.UUID;

public record TreatmentConsentResponse(
        UUID id,
        UUID treatmentPlanId,
        UUID patientId,
        UUID treatmentBudgetId,
        String documentVersion,
        String consentText,
        TreatmentConsentStatus status,
        TreatmentPlanProfessionalResponse preparedBy,
        TreatmentPlanProfessionalResponse acceptedBy,
        TreatmentPlanProfessionalResponse revokedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant revokedAt) {
}
