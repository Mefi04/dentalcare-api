package com.dentalcare.api.modules.clinicalrecords.storage;

import java.time.Instant;

public record StoredDocumentMetadata(
        String storageObjectKey,
        long contentLength,
        String contentType,
        Instant lastModified
) {
    public StoredDocumentMetadata {
        if (storageObjectKey == null || storageObjectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage object key is required");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "application/octet-stream";
        }
        if (contentLength < 0) {
            contentLength = 0L;
        }
        if (lastModified == null) {
            lastModified = Instant.now();
        }
    }
}
