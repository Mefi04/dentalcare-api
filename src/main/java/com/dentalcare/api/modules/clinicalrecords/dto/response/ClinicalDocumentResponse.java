package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClinicalDocumentResponse(
        UUID id,
        UUID patientId,
        ClinicalProfessionalResponse author,
        String title,
        ClinicalDocumentType type,
        String description,
        LocalDate documentDate,
        Instant createdAt,
        Instant updatedAt) {
}
