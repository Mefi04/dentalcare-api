package com.dentalcare.api.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mobile session refresh response containing rotated tokens")
public record MobileRefreshResponse(
        @Schema(description = "New JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "New rotated opaque refresh token for mobile secure storage", example = "e5a3c2d1f9b8a7c6e5d4c3b2a1098765")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "1800")
        long expiresIn
) {
}
