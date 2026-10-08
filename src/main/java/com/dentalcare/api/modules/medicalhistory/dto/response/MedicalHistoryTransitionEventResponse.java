package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireStatus;
import java.time.Instant;
import java.util.UUID;

public record MedicalHistoryTransitionEventResponse(QuestionnaireStatus fromStatus,QuestionnaireStatus toStatus,
        UUID actorId,String actorName,String reasonCode,Instant occurredAt) {}
