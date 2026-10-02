package com.dentalcare.api.modules.clinicalrecords.dto.request;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateClinicalDocumentRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede exceder 150 caracteres")
        String title,

        @NotNull(message = "El tipo de documento es obligatorio")
        ClinicalDocumentType type,

        @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
        String description,

        LocalDate documentDate) {
}
