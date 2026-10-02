package com.dentalcare.api.modules.clinicalrecords.storage;

import java.io.InputStream;
import java.util.UUID;

public interface ClinicalDocumentStorage {

    StoredDocument store(UploadDocumentCommand command);

    default StoredDocument store(UUID patientId, String originalFileName, String contentType, long contentLength, InputStream inputStream) {
        return store(new UploadDocumentCommand(patientId, originalFileName, contentType, contentLength, inputStream));
    }

    StoredDocumentContent load(String storageObjectKey);

    StoredDocumentMetadata getMetadata(String storageObjectKey);

    boolean exists(String storageObjectKey);

    void delete(String storageObjectKey);
}
