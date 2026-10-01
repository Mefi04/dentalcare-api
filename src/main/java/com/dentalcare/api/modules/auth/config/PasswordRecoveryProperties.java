package com.dentalcare.api.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dentalcare.password-recovery")
public record PasswordRecoveryProperties(Duration expiration, int maxAttempts, String mailFrom) {

    public PasswordRecoveryProperties {
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            expiration = Duration.ofMinutes(15);
        }
        if (maxAttempts < 1) {
            maxAttempts = 5;
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            mailFrom = "no-reply@dentalcare.local";
        }
    }
}
