package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PatientTreatmentBudgetService {
    Page<PatientTreatmentBudgetResponse> findMine(UUID authenticatedUserId, int page, int size);
    PatientTreatmentBudgetResponse findMineById(UUID authenticatedUserId, UUID budgetId);
    PatientTreatmentBudgetResponse accept(UUID authenticatedUserId, UUID budgetId);
    PatientTreatmentBudgetResponse reject(UUID authenticatedUserId, UUID budgetId);
}
