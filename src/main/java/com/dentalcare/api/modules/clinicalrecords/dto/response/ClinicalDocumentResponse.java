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
        String fileName,
        Long fileSize,
        String contentType,
        boolean hasFile,
        boolean patientVisible,
        Instant sharedAt,
        ClinicalProfessionalResponse sharedBy,
        Instant createdAt,
        Instant updatedAt) {

    public ClinicalDocumentResponse(
            UUID id,
            UUID patientId,
            ClinicalProfessionalResponse author,
            String title,
            ClinicalDocumentType type,
            String description,
            LocalDate documentDate,
            Instant createdAt,
            Instant updatedAt) {
        this(id, patientId, author, title, type, description, documentDate,
                null, null, null, false, false, null, null, createdAt, updatedAt);
    }

    public ClinicalDocumentResponse(
            UUID id,
            UUID patientId,
            ClinicalProfessionalResponse author,
            String title,
            ClinicalDocumentType type,
            String description,
            LocalDate documentDate,
            String fileName,
            Long fileSize,
            String contentType,
            boolean hasFile,
            Instant createdAt,
            Instant updatedAt) {
        this(id, patientId, author, title, type, description, documentDate,
                fileName, fileSize, contentType, hasFile, false, null, null, createdAt, updatedAt);
    }
}
