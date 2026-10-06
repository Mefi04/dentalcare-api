package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PatientTreatmentPlanService {
    Page<PatientTreatmentPlanResponse> findMine(UUID authenticatedUserId, int page, int size);

    PatientTreatmentPlanResponse findMineById(UUID authenticatedUserId, UUID planId);
}
