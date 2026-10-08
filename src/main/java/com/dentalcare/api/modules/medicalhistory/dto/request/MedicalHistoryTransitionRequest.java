package com.dentalcare.api.modules.medicalhistory.dto.request;

import jakarta.validation.constraints.*;

public record MedicalHistoryTransitionRequest(@PositiveOrZero long lockVersion,@NotBlank @Size(max=1000) String reason) {}
