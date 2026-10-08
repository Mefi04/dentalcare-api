package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.medicalhistory.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

public record MedicalHistoryQuestionnaireResponse(UUID id,UUID patientId,String patientName,
        QuestionnairePurpose purpose,QuestionnaireSource source,QuestionnaireStatus status,long lockVersion,
        UUID templateVersionId,int templateVersion,String templateTitle,MedicalHistoryTemplateResponse template,Instant expiresAt,Instant deliveredAt,
        Instant receivedAt,Instant submittedAt,Instant reviewStartedAt,Instant validatedAt,String statusReason,
        UUID scanDocumentId,Instant createdAt,Instant updatedAt,List<Answer> answers,List<ReviewNote> reviewNotes,
        List<Attestation> attestations) {
    public record Answer(UUID questionId,String questionKey,String prompt,MedicalHistoryAnswerType answerType,
                         JsonNode value,String note) {}
    public record ReviewNote(UUID id,MedicalHistoryReviewNoteType type,String text,UUID authorId,String authorName,Instant createdAt) {}
    public record Attestation(MedicalHistoryAttestationType type,int revisionNumber,String signerName,Instant attestedAt,UUID evidenceDocumentId) {}
}
