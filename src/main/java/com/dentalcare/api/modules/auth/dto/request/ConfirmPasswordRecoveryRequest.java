package com.dentalcare.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordRecoveryRequest(
        @NotBlank(message = "CUI is required")
        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits")
        String cui,
        @NotBlank(message = "Recovery code is required")
        @Pattern(regexp = "^[0-9]{8}$", message = "Recovery code must contain exactly 8 digits")
        String code,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
        String newPassword) {
}
