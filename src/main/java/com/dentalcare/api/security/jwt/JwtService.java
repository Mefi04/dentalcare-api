package com.dentalcare.api.security.jwt;

import java.util.List;
import java.util.UUID;

public interface JwtService {
    String createAccessToken(UUID userId, List<String> authorities);
    AccessTokenClaims parseAccessToken(String token);
    long getAccessTokenLifetimeSeconds();

    record AccessTokenClaims(UUID userId, List<String> authorities) {
    }
}
