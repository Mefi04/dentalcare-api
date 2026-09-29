package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.modules.medicalhistory.dto.request.UpdateMedicalHistoryRequest;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;

import java.util.UUID;

public interface MedicalHistoryService {

    MedicalHistoryResponse findByPatientId(UUID patientId);

    MedicalHistoryResponse update(UUID patientId, UpdateMedicalHistoryRequest request);

    PatientHealthResponse findForAuthenticatedPatient(UUID authenticatedUserId);
}
