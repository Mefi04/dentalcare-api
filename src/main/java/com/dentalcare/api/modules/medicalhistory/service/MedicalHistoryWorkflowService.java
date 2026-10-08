package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.modules.medicalhistory.dto.request.*;
import com.dentalcare.api.modules.medicalhistory.dto.response.*;
import com.dentalcare.api.modules.medicalhistory.model.*;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.*;

public interface MedicalHistoryWorkflowService {
    MedicalHistoryResponse importLegacy(UUID patientId,UpdateMedicalHistoryRequest request,UUID actorId);
    MedicalHistoryTemplateResponse createTemplate(CreateMedicalHistoryTemplateRequest request,UUID actorId);
    MedicalHistoryTemplateResponse createTemplateVersion(UUID templateId,CreateMedicalHistoryTemplateVersionRequest request,UUID actorId);
    MedicalHistoryTemplateResponse updateTemplateVersion(UUID versionId,CreateMedicalHistoryTemplateVersionRequest request,UUID actorId);
    MedicalHistoryTemplateResponse publishTemplate(UUID versionId,UUID actorId);
    MedicalHistoryTemplateResponse getTemplate(UUID versionId);
    MedicalHistoryTemplateResponse getCurrentTemplate();
    MedicalHistoryQuestionnaireResponse assign(UUID patientId,AssignMedicalHistoryQuestionnaireRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse createChangeProposal(CreateMedicalHistoryChangeProposalRequest request,UUID userId);
    Page<MedicalHistoryQuestionnaireSummaryResponse> search(UUID patientId,QuestionnaireStatus status,QuestionnaireSource source,Instant from,Instant to,int page,int size);
    Page<MedicalHistoryQuestionnaireSummaryResponse> listMine(UUID userId,int page,int size);
    MedicalHistoryQuestionnaireResponse getForStaff(UUID patientId,UUID questionnaireId);
    MedicalHistoryQuestionnaireResponse getMine(UUID userId,UUID questionnaireId);
    MedicalHistoryQuestionnaireSummaryResponse markDelivered(UUID patientId,UUID questionnaireId,UUID actorId);
    MedicalHistoryQuestionnaireSummaryResponse markReceived(UUID patientId,UUID questionnaireId,ReceiveMedicalHistoryQuestionnaireRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse saveAnswersForStaff(UUID patientId,UUID questionnaireId,SaveMedicalHistoryAnswersRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse saveMyAnswers(UUID userId,UUID questionnaireId,SaveMedicalHistoryAnswersRequest request);
    MedicalHistoryQuestionnaireResponse submitForStaff(UUID patientId,UUID questionnaireId,SubmitMedicalHistoryQuestionnaireRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse submitMine(UUID userId,UUID questionnaireId,SubmitMedicalHistoryQuestionnaireRequest request);
    MedicalHistoryQuestionnaireResponse startReview(UUID patientId,UUID questionnaireId,MedicalHistoryVersionedActionRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse addReviewNote(UUID patientId,UUID questionnaireId,MedicalHistoryReviewNoteRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse requestClarification(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse validate(UUID patientId,UUID questionnaireId,MedicalHistoryVersionedActionRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse reject(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse cancelForStaff(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId);
    MedicalHistoryQuestionnaireResponse cancelMine(UUID userId,UUID questionnaireId,MedicalHistoryTransitionRequest request);
    Page<MedicalHistoryVersionResponse> listVersions(UUID patientId,int page,int size);
    MedicalHistoryVersionResponse getVersion(UUID patientId,UUID versionId);
    MedicalHistoryVersionResponse getMyCurrentVersion(UUID userId);
    List<MedicalHistoryTransitionEventResponse> audit(UUID patientId,UUID questionnaireId);
}
