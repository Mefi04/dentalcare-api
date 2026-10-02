package com.dentalcare.api.modules.clinicalrecords.storage;

import java.io.InputStream;
import java.util.UUID;

public record UploadDocumentCommand(
        UUID patientId,
        String originalFileName,
        String contentType,
        long contentLength,
        InputStream inputStream
) {
    public UploadDocumentCommand {
        if (patientId == null) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Original file name is required");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Content type is required");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length must be non-negative");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream is required");
        }
    }
}
