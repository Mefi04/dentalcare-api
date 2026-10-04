package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetItemResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetItem;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class TreatmentBudgetMapper {
    public TreatmentBudgetResponse toResponse(TreatmentBudget budget) {
        return new TreatmentBudgetResponse(
                budget.getId(), budget.getTreatmentPlan().getId(), budget.getPatient().getId(),
                budget.getVersion(), budget.getPlanUpdatedAt(), budget.getSubtotal(), budget.getTotal(),
                budget.getStatus(), actor(budget.getGeneratedBy()), actor(budget.getDecidedBy()),
                budget.getItems().stream()
                        .sorted(Comparator.comparing(TreatmentBudgetItem::getPosition))
                        .map(item -> new TreatmentBudgetItemResponse(
                                item.getTreatmentPlanItemId(), item.getName(), item.getTooth(),
                                item.getQuantity(), item.getUnitPrice(), item.getSubtotal(), item.getPosition()))
                        .toList(),
                budget.getCreatedAt(), budget.getUpdatedAt(), budget.getDecidedAt());
    }

    private TreatmentPlanProfessionalResponse actor(User user) {
        return user == null ? null : new TreatmentPlanProfessionalResponse(user.getId(), user.getFullName());
    }
}
