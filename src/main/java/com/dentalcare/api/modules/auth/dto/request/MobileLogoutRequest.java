package com.dentalcare.api.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Mobile logout request")
public record MobileLogoutRequest(
        @Schema(description = "Opaque refresh token to revoke", example = "d94b0f9c2a8e4b7c89f1d0a5b6e7f8a9")
        @Size(max = 255, message = "Refresh token must not exceed 255 characters")
        String refreshToken
) {
}
