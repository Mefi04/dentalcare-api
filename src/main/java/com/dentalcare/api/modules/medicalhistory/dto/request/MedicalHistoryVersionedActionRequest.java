package com.dentalcare.api.modules.medicalhistory.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record MedicalHistoryVersionedActionRequest(@PositiveOrZero long lockVersion) {}
