package com.dentalcare.api.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeInitialPasswordRequest(
        @Schema(description = "Permanent password to set for the account")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
        String newPassword,
        @Schema(description = "Must exactly match newPassword")
        @NotBlank(message = "Password confirmation is required")
        String confirmation) {
}
