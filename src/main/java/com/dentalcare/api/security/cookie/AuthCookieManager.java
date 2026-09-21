package com.dentalcare.api.security.cookie;

import com.dentalcare.api.security.jwt.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieManager {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_LAX = "Lax";

    private final JwtProperties jwtProperties;

    public AuthCookieManager(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie createRefreshCookie(String rawToken, Duration maxAge, boolean isRequestSecure) {
        boolean secure = jwtProperties.cookieSecure() || isRequestSecure;
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_LAX)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge != null && !maxAge.isNegative() ? maxAge : Duration.ZERO)
                .build();
    }

    public ResponseCookie createClearCookie(boolean isRequestSecure) {
        boolean secure = jwtProperties.cookieSecure() || isRequestSecure;
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_LAX)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
