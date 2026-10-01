package com.dentalcare.api.modules.prescriptions.service;

import com.dentalcare.api.modules.prescriptions.dto.request.CreatePrescriptionRequest;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PrescriptionService {
    PrescriptionResponse create(UUID patientId, UUID professionalId, CreatePrescriptionRequest request);

    Page<PrescriptionResponse> findByPatient(UUID patientId, int page, int size);

    PrescriptionResponse findById(UUID prescriptionId);

    Page<PrescriptionResponse> findMine(UUID authenticatedUserId, int page, int size);

    PrescriptionResponse findMineById(UUID authenticatedUserId, UUID prescriptionId);
}
