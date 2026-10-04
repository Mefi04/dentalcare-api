package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetResponse;

import java.util.List;
import java.util.UUID;

public interface TreatmentBudgetService {
    TreatmentBudgetResponse generate(UUID planId, UUID actorId);
    List<TreatmentBudgetResponse> findByPlan(UUID planId);
    TreatmentBudgetResponse findById(UUID budgetId);
    TreatmentBudgetResponse approve(UUID budgetId, UUID actorId);
    TreatmentBudgetResponse reject(UUID budgetId, UUID actorId);
}
