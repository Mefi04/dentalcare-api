package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ClinicalDocumentService {

    ClinicalDocumentResponse createDocument(UUID patientId, CreateClinicalDocumentRequest request, UUID authenticatedUserId);

    Page<ClinicalDocumentResponse> findDocumentsByPatient(UUID patientId, ClinicalDocumentType type, int page, int size);

    ClinicalDocumentResponse findDocumentById(UUID patientId, UUID documentId);
}
