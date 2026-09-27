package com.dentalcare.api.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mobile authentication response containing tokens and user view")
public record MobileLoginResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Opaque refresh token for mobile secure storage", example = "d94b0f9c2a8e4b7c89f1d0a5b6e7f8a9")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "1800")
        long expiresIn,

        @Schema(description = "Authenticated user profile and permissions")
        UserResponse user
) {
}
