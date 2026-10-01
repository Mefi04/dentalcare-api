package com.dentalcare.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordRecoveryRequest(
        @NotBlank(message = "CUI is required")
        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits")
        String cui) {
}
