package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.request.CompleteTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentProcedureResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TreatmentProcedureService {
    TreatmentProcedureResponse register(UUID planId, UUID professionalId, CreateTreatmentProcedureRequest request);
    TreatmentProcedureResponse complete(UUID procedureId, UUID professionalId,
                                        CompleteTreatmentProcedureRequest request);
    TreatmentProcedureResponse findById(UUID procedureId);
    Page<TreatmentProcedureResponse> findByPlan(UUID planId, int page, int size);
    Page<TreatmentProcedureResponse> findByPatient(UUID patientId, int page, int size);
}
