package com.dentalcare.api.exception;

public class UnsupportedMediaTypeException extends BadRequestException {
    public UnsupportedMediaTypeException(String message) { super(message); }
}
