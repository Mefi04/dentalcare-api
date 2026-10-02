package com.dentalcare.api.modules.clinicalrecords.dto.request;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Schema(description = "Payload for uploading and registering a clinical document with an attached file")
public record UploadClinicalDocumentRequest(
        @NotNull(message = "File is required")
        @Schema(description = "Clinical document file (PDF, JPEG, PNG)", type = "string", format = "binary")
        MultipartFile file,

        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        @Schema(description = "Document title", example = "Radiografía Panorámica")
        String title,

        @NotNull(message = "Type is required")
        @Schema(description = "Document category", example = "RADIOGRAPHY")
        ClinicalDocumentType type,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Optional clinical document description", example = "Estudio radiológico de control previo a ortodoncia")
        String description,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Date of the document. Defaults to current date if not provided", example = "2026-10-01")
        LocalDate documentDate
) {
}
