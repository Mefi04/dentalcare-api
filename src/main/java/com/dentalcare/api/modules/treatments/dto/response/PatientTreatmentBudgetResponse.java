package com.dentalcare.api.modules.treatments.dto.response;

import com.dentalcare.api.modules.treatments.model.PatientBudgetDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatientTreatmentBudgetResponse(
        UUID id,
        UUID treatmentPlanId,
        String treatmentPlanName,
        Integer version,
        BigDecimal subtotal,
        BigDecimal total,
        PatientBudgetDecision decision,
        List<PatientTreatmentBudgetItemResponse> items,
        Instant issuedAt,
        Instant clinicApprovedAt,
        Instant patientDecidedAt) {
}
