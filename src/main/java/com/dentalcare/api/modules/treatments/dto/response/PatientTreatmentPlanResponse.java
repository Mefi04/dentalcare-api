package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatientTreatmentPlanResponse(
        UUID id,
        String name,
        TreatmentPlanStatus status,
        String professionalFullName,
        Instant createdAt,
        Instant approvedAt,
        long plannedQuantity,
        long completedQuantity,
        int progressPercentage,
        List<PatientTreatmentPlanItemResponse> items) {
}
