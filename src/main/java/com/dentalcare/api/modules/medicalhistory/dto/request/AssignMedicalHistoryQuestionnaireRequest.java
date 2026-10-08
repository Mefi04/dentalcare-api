package com.dentalcare.api.modules.medicalhistory.dto.request;

import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireSource;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignMedicalHistoryQuestionnaireRequest(@NotNull QuestionnaireSource source,Instant expiresAt,UUID scanDocumentId) {}
