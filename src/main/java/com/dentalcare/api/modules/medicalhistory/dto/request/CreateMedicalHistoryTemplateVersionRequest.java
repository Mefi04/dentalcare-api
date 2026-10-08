package com.dentalcare.api.modules.medicalhistory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateMedicalHistoryTemplateVersionRequest(@NotBlank @Size(max=180) String title,
        @Valid @NotEmpty @Size(max=30) List<CreateMedicalHistoryTemplateRequest.Section> sections) {}
