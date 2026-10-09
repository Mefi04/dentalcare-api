package com.dentalcare.api.security.cookie;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class AuthCookieManager {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public static final String TAB_SESSION_HEADER_NAME = "X-Client-Session-Id";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_LAX = "Lax";

    private final JwtProperties jwtProperties;

    public AuthCookieManager(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie createRefreshCookie(String rawToken, Duration maxAge, boolean isRequestSecure) {
        return createRefreshCookie(rawToken, maxAge, isRequestSecure, null);
    }

    public ResponseCookie createRefreshCookie(String rawToken, Duration maxAge, boolean isRequestSecure,
                                              String tabSessionId) {
        boolean secure = jwtProperties.cookieSecure() || isRequestSecure;
        return ResponseCookie.from(cookieNameForTab(tabSessionId), rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_LAX)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge != null && !maxAge.isNegative() ? maxAge : Duration.ZERO)
                .build();
    }

    public ResponseCookie createClearCookie(boolean isRequestSecure) {
        return createClearCookie(isRequestSecure, null);
    }

    public ResponseCookie createClearCookie(boolean isRequestSecure, String tabSessionId) {
        boolean secure = jwtProperties.cookieSecure() || isRequestSecure;
        return ResponseCookie.from(cookieNameForTab(tabSessionId), "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_LAX)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public String findRefreshToken(HttpServletRequest request, String tabSessionId) {
        String expectedCookieName = cookieNameForTab(tabSessionId);
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (expectedCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String cookieNameForTab(String tabSessionId) {
        if (tabSessionId == null) {
            return REFRESH_TOKEN_COOKIE_NAME;
        }
        try {
            UUID tabId = UUID.fromString(tabSessionId);
            if (!tabId.toString().equalsIgnoreCase(tabSessionId)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return REFRESH_TOKEN_COOKIE_NAME + "-" + tabId;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid client session identifier");
        }
    }
}
