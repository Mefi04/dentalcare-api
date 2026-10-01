package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.request.UpdateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface TreatmentPlanService {
    Page<TreatmentPlanResponse> findByPatient(UUID patientId, int page, int size);
    TreatmentPlanResponse create(UUID patientId, CreateTreatmentPlanRequest request);
    TreatmentPlanResponse findById(UUID planId);
    TreatmentPlanResponse update(UUID planId, UpdateTreatmentPlanRequest request);
    TreatmentPlanResponse approve(UUID planId);
    List<TreatmentPlanProfessionalResponse> findProfessionals();
}
