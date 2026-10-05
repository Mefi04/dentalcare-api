package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetItemResponse;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class PatientTreatmentBudgetMapper {
    public PatientTreatmentBudgetResponse toResponse(TreatmentBudget budget) {
        return new PatientTreatmentBudgetResponse(
                budget.getId(), budget.getTreatmentPlan().getId(), budget.getTreatmentPlan().getName(),
                budget.getVersion(), budget.getSubtotal(), budget.getTotal(), budget.getPatientDecision(),
                budget.getItems().stream()
                        .sorted(Comparator.comparing(TreatmentBudgetItem::getPosition))
                        .map(item -> new PatientTreatmentBudgetItemResponse(
                                item.getTreatmentPlanItemId(), item.getName(), item.getTooth(),
                                item.getQuantity(), item.getUnitPrice(), item.getSubtotal(), item.getPosition()))
                        .toList(), budget.getCreatedAt(), budget.getDecidedAt(), budget.getPatientDecidedAt());
    }
}
