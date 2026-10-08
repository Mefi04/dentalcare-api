package com.dentalcare.api.modules.medicalhistory.dto.request;

import jakarta.validation.constraints.*;

public record MedicalHistoryReviewNoteRequest(@NotBlank @Size(max=2000) String note) {}
