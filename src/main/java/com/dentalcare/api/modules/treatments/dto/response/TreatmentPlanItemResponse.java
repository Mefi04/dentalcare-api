package com.dentalcare.api.modules.treatments.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TreatmentPlanItemResponse(
        UUID id,
        String name,
        String tooth,
        Integer quantity,
        BigDecimal unitPrice,
        Integer position,
        BigDecimal subtotal) {
}
