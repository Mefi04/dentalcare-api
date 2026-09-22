package com.dentalcare.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "Guatemalan CUI/DPI number", example = "1234567890123", pattern = "^[0-9]{13}$")
        @NotBlank(message = "CUI is required")
        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits") String cui,
        @NotBlank(message = "Password is required") String password) {
}
