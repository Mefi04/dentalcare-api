package com.dentalcare.api.modules.clinicalrecords.storage.exception;

public class DocumentNotFoundInStorageException extends DocumentStorageException {

    public DocumentNotFoundInStorageException(String message) {
        super(message);
    }

    public DocumentNotFoundInStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
