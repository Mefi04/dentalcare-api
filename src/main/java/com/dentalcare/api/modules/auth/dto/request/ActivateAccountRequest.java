package com.dentalcare.api.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @Schema(description = "Guatemalan CUI/DPI number", example = "1234567890123", pattern = "^[0-9]{13}$")
        @NotBlank(message = "CUI is required")
        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits")
        String cui,

        @Schema(description = "Temporary password assigned at account creation", example = "tempPass123!@#")
        @NotBlank(message = "Temporary password is required")
        String temporaryPassword,

        @Schema(description = "New permanent password", example = "NewSecurePassword123!")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
        String newPassword) {
}
