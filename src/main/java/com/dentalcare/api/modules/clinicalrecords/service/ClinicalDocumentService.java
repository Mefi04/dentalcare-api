package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UploadClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ClinicalDocumentService {

    ClinicalDocumentResponse createDocument(UUID patientId, CreateClinicalDocumentRequest request, UUID authenticatedUserId);

    ClinicalDocumentResponse uploadDocument(UUID patientId, UploadClinicalDocumentRequest request, UUID authenticatedUserId);

    ClinicalDocumentDownload downloadDocument(UUID patientId, UUID documentId);

    Page<ClinicalDocumentResponse> findDocumentsByPatient(UUID patientId, ClinicalDocumentType type, int page, int size);

    ClinicalDocumentResponse findDocumentById(UUID patientId, UUID documentId);
}
