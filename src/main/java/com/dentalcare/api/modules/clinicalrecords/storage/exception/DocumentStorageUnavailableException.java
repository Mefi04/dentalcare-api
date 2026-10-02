package com.dentalcare.api.modules.clinicalrecords.storage.exception;

public class DocumentStorageUnavailableException extends DocumentStorageException {

    public DocumentStorageUnavailableException(String message) {
        super(message);
    }

    public DocumentStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
