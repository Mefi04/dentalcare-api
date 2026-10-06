package com.dentalcare.api.modules.treatments.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PatientTreatmentBudgetItemResponse(
        UUID treatmentPlanItemId,
        String name,
        String tooth,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        Integer position) {
}
