package com.dentalcare.api.modules.medicalhistory.dto.request;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryAttestationType;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record SubmitMedicalHistoryQuestionnaireRequest(@PositiveOrZero long lockVersion,
        @NotNull MedicalHistoryAttestationType attestationType,@NotBlank @Size(max=180) String signerName,
        UUID evidenceDocumentId) {}
