package com.dentalcare.api.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dentalcare.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        Duration accessExpiration,
        Duration refreshExpiration,
        Duration refreshInactivityTimeout,
        boolean cookieSecure) {

    public JwtProperties {
        if (accessExpiration == null) {
            accessExpiration = Duration.ofMinutes(30);
        }
        if (refreshExpiration == null) {
            refreshExpiration = Duration.ofDays(7);
        }
        if (refreshInactivityTimeout == null) {
            refreshInactivityTimeout = Duration.ofHours(24);
        }
    }
}
