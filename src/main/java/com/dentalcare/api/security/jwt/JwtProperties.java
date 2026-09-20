package com.dentalcare.api.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dentalcare.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        Duration accessExpiration,
        Duration refreshExpiration) {
}
