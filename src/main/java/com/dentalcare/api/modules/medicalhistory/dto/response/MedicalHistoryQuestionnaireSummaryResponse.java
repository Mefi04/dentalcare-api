package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.medicalhistory.model.*;
import java.time.Instant;
import java.util.UUID;

public record MedicalHistoryQuestionnaireSummaryResponse(UUID id,UUID patientId,String patientName,
        QuestionnairePurpose purpose,QuestionnaireSource source,QuestionnaireStatus status,long lockVersion,
        int templateVersion,String templateTitle,Instant expiresAt,Instant submittedAt,Instant validatedAt,
        String statusReason,Instant updatedAt) {}
