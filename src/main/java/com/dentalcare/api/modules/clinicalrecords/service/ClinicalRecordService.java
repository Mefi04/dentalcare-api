package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ClinicalRecordService {

    ClinicalAttentionResponse createAttention(UUID patientId, CreateClinicalAttentionRequest request, UUID authenticatedUserId);

    Page<ClinicalAttentionResponse> findAttentionsByPatient(UUID patientId, int page, int size);

    ClinicalAttentionResponse findAttentionById(UUID attentionId);

    ClinicalDiagnosisResponse createDiagnosis(UUID attentionId, CreateClinicalDiagnosisRequest request, UUID authenticatedUserId);

    Page<ClinicalDiagnosisResponse> findDiagnosesByPatient(UUID patientId, DiagnosisType type, int page, int size);

    ClinicalEvolutionResponse createEvolutionNote(UUID attentionId, CreateEvolutionNoteRequest request, UUID authenticatedUserId);

    Page<ClinicalEvolutionResponse> findEvolutionNotesByPatient(UUID patientId, int page, int size);

    OdontogramFindingResponse createOdontogramFinding(UUID patientId, CreateOdontogramFindingRequest request, UUID authenticatedUserId);

    OdontogramResponse findCurrentOdontogram(UUID patientId, DentitionType dentition);

    Page<OdontogramFindingResponse> findOdontogramFindingsByPatient(UUID patientId, int page, int size);

    Page<ClinicalHistoryEntryResponse> findClinicalHistory(UUID patientId, int page, int size);

    ClinicalRecordSummaryResponse findSummaryByPatient(UUID patientId);
}
