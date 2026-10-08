package com.dentalcare.api.modules.medicalhistory.dto.request;

import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireSource;
import jakarta.validation.constraints.NotNull;

public record CreateMedicalHistoryChangeProposalRequest(@NotNull QuestionnaireSource source) {}
