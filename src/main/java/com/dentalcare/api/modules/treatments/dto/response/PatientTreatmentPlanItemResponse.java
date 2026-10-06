package com.dentalcare.api.modules.treatments.dto.response;

import java.util.List;
import java.util.UUID;

public record PatientTreatmentPlanItemResponse(
        UUID id,
        String name,
        String tooth,
        Integer plannedQuantity,
        long completedQuantity,
        long inProgressQuantity,
        int progressPercentage,
        PatientTreatmentItemStatus status,
        List<PatientTreatmentProcedureResponse> procedures) {
}
