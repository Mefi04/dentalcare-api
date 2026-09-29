package com.dentalcare.api.modules.medicalhistory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateMedicalHistoryRequest(
        @NotNull @Size(max = 100) List<@Valid @NotBlank @Size(max = 200) String> allergies,
        @NotNull @Size(max = 100) List<@Valid @NotBlank @Size(max = 200) String> currentMedications,
        @NotNull @Size(max = 100) List<@Valid @NotBlank @Size(max = 200) String> relevantConditions,
        @Size(max = 4000) String observations) {
}
