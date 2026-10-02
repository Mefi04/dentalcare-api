package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalPreparationRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalPreparationResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.CurrentAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ClinicalRecordService {

    ClinicalAttentionResponse createAttention(UUID patientId, CreateClinicalAttentionRequest request, UUID authenticatedUserId);

    Page<ClinicalAttentionResponse> findAttentionsByPatient(UUID patientId, int page, int size);

    ClinicalAttentionResponse findAttentionById(UUID attentionId);

    ClinicalAttentionResponse findAttentionById(UUID patientId, UUID attentionId);

    ClinicalDiagnosisResponse createDiagnosis(UUID attentionId, CreateClinicalDiagnosisRequest request, UUID authenticatedUserId);

    ClinicalDiagnosisResponse createDiagnosis(UUID patientId, UUID attentionId, CreateClinicalDiagnosisRequest request, UUID authenticatedUserId);

    Page<ClinicalDiagnosisResponse> findDiagnosesByPatient(UUID patientId, DiagnosisType type, int page, int size);

    ClinicalDiagnosisResponse findDiagnosisById(UUID patientId, UUID diagnosisId);

    ClinicalEvolutionResponse createEvolutionNote(UUID attentionId, CreateEvolutionNoteRequest request, UUID authenticatedUserId);

    ClinicalEvolutionResponse createEvolutionNote(UUID patientId, UUID attentionId, CreateEvolutionNoteRequest request, UUID authenticatedUserId);

    Page<ClinicalEvolutionResponse> findEvolutionNotesByPatient(UUID patientId, int page, int size);

    ClinicalEvolutionResponse findEvolutionNoteById(UUID patientId, UUID evolutionId);

    OdontogramFindingResponse createOdontogramFinding(UUID patientId, CreateOdontogramFindingRequest request, UUID authenticatedUserId);

    OdontogramResponse findCurrentOdontogram(UUID patientId, DentitionType dentition);

    Page<OdontogramFindingResponse> findOdontogramFindingsByPatient(UUID patientId, int page, int size);

    Page<OdontogramFindingResponse> findOdontogramFindingsByPatient(
            UUID patientId, String toothCode, DentitionType dentition, ToothFinding finding, int page, int size);

    Page<ClinicalHistoryEntryResponse> findClinicalHistory(UUID patientId, int page, int size);

    ClinicalRecordSummaryResponse findSummaryByPatient(UUID patientId);

    CurrentAttentionResponse findCurrentAttention(UUID patientId);

    ClinicalPreparationResponse createPreparation(UUID patientId, UUID attentionId, CreateClinicalPreparationRequest request, UUID authenticatedUserId);

    Page<ClinicalPreparationResponse> findPreparationsByPatient(UUID patientId, int page, int size);

    ClinicalPreparationResponse findPreparationById(UUID patientId, UUID preparationId);

    ClinicalPreparationResponse findLatestPreparationByPatient(UUID patientId);

    ClinicalPreparationResponse findPreparationByAttention(UUID patientId, UUID attentionId);
}
