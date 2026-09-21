package com.dentalcare.api.modules.auth.dto.response;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
