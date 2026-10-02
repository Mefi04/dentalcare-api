package com.dentalcare.api.modules.clinicalrecords.storage;

public record StoredDocument(
        String storageObjectKey,
        String fileName,
        long fileSize,
        String contentType
) {
    public StoredDocument {
        if (storageObjectKey == null || storageObjectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage object key is required");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name is required");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size must be non-negative");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Content type is required");
        }
    }
}
