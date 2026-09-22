package com.dentalcare.api.modules.patients.service;

import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientRequest;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PatientService {

    PatientResponse create(CreatePatientRequest request);

    PatientResponse findById(UUID id);

    Page<PatientResponse> search(int page, int size, String search);

    PatientResponse update(UUID id, UpdatePatientRequest request);
}
