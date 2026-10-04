package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TreatmentBudgetResponse(
        UUID id,
        UUID treatmentPlanId,
        UUID patientId,
        Integer version,
        Instant planUpdatedAt,
        BigDecimal subtotal,
        BigDecimal total,
        TreatmentBudgetStatus status,
        TreatmentPlanProfessionalResponse generatedBy,
        TreatmentPlanProfessionalResponse decidedBy,
        List<TreatmentBudgetItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant decidedAt) {
}
