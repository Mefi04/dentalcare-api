package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientClinicalDocumentResponse(
        UUID id,
        ClinicalProfessionalResponse author,
        String title,
        ClinicalDocumentType type,
        String description,
        LocalDate documentDate,
        String fileName,
        Long fileSize,
        String contentType,
        boolean hasFile,
        Instant sharedAt,
        Instant createdAt,
        Instant updatedAt) {
}
