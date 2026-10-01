package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TreatmentPlanResponse(
        UUID id,
        UUID patientId,
        String name,
        String observations,
        TreatmentPlanProfessionalResponse professional,
        TreatmentPlanStatus status,
        List<TreatmentPlanItemResponse> items,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt) {
}
