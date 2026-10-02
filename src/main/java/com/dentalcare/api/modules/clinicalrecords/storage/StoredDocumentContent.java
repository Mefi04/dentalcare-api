package com.dentalcare.api.modules.clinicalrecords.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public record StoredDocumentContent(
        String storageObjectKey,
        String contentType,
        long contentLength,
        InputStream content
) implements Closeable {

    public StoredDocumentContent {
        if (storageObjectKey == null || storageObjectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage object key is required");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "application/octet-stream";
        }
        if (contentLength < 0) {
            contentLength = 0L;
        }
    }

    @Override
    public void close() throws IOException {
        if (content != null) {
            content.close();
        }
    }
}
