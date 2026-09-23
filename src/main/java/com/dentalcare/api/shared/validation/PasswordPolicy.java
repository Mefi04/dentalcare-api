package com.dentalcare.api.shared.validation;

import com.dentalcare.api.exception.BadRequestException;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final String PASSWORD_SIZE_MESSAGE = "New password must be between 8 and 128 characters";
    public static final String PASSWORD_REQUIRED_MESSAGE = "New password is required";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        return password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException(PASSWORD_REQUIRED_MESSAGE);
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BadRequestException(PASSWORD_SIZE_MESSAGE);
        }
    }
}
