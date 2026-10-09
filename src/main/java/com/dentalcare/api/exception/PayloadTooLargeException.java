package com.dentalcare.api.exception;

public class PayloadTooLargeException extends BadRequestException {
    public PayloadTooLargeException(String message) { super(message); }
}
