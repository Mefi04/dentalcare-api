package com.dentalcare.api.modules.auth.dto.response;

public record RefreshResponse(String accessToken, String tokenType, long expiresIn) {
}
