package com.dentalcare.api.modules.clinicalrecords.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateEvolutionNoteRequest(
        @NotNull(message = "Consultation date is required")
        LocalDate consultationDate,

        @NotBlank(message = "Procedure summary is required")
        @Size(max = 300, message = "Procedure summary cannot exceed 300 characters")
        String procedureSummary,

        @NotBlank(message = "Evolution note is required")
        @Size(max = 4000, message = "Evolution note cannot exceed 4000 characters")
        String note) {
}
