package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public record ClinicalDocumentDownload(
        InputStream inputStream,
        String fileName,
        String contentType,
        long contentLength
) implements Closeable {

    public ClinicalDocumentDownload {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream is required");
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "document";
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            inputStream.close();
        }
    }
}
