package com.dentalcare.api.modules.medicalhistory.dto.request;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryAnswerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CreateMedicalHistoryTemplateRequest(
        @NotBlank @Size(max=80) String code,
        @NotBlank @Size(max=150) String name,
        @NotBlank @Size(max=180) String title,
        @NotEmpty @Size(max=30) List<@Valid Section> sections) {
    public record Section(@NotBlank @Size(max=80) String key,@NotBlank @Size(max=180) String title,
                          @Size(max=500) String description,@NotEmpty @Size(max=100) List<@Valid Question> questions) {}
    public record Question(@NotBlank @Size(max=100) String key,@NotBlank @Size(max=500) String prompt,
                           @NotNull MedicalHistoryAnswerType answerType,boolean required,boolean notesAllowed,
                           @Min(1) @Max(4000) Integer maxLength,BigDecimal minValue,BigDecimal maxValue,
                           @Size(max=100) List<@Size(max=200) String> choices,
                           @Size(max=1000) String conditionalNoteRule) {}
}
