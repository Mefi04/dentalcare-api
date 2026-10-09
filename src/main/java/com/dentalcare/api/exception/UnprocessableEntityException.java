package com.dentalcare.api.exception;

public class UnprocessableEntityException extends BadRequestException {
    public UnprocessableEntityException(String message) { super(message); }
    public UnprocessableEntityException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
