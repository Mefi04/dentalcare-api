package com.dentalcare.api.modules.clinicalrecords.storage.r2;

import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocument;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentContent;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentMetadata;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageDisabledException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dentalcare.r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledClinicalDocumentStorage implements ClinicalDocumentStorage {

    private static final String DISABLED_MESSAGE = "Cloudflare R2 storage is disabled";

    @Override
    public StoredDocument store(UploadDocumentCommand command) {
        throw new DocumentStorageDisabledException(DISABLED_MESSAGE);
    }

    @Override
    public StoredDocumentContent load(String storageObjectKey) {
        throw new DocumentStorageDisabledException(DISABLED_MESSAGE);
    }

    @Override
    public StoredDocumentMetadata getMetadata(String storageObjectKey) {
        throw new DocumentStorageDisabledException(DISABLED_MESSAGE);
    }

    @Override
    public boolean exists(String storageObjectKey) {
        throw new DocumentStorageDisabledException(DISABLED_MESSAGE);
    }

    @Override
    public void delete(String storageObjectKey) {
        throw new DocumentStorageDisabledException(DISABLED_MESSAGE);
    }
}
