package com.dentalcare.api.security.jwt;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JjwtServiceTests {
    private KeyPair keyPair;
    private JjwtService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        service = serviceWithExpiration(Duration.ofMinutes(30));
    }

    @Test
    void generatedAccessTokenContainsOnlyRequiredAuthenticationClaims() {
        UUID userId = UUID.randomUUID();
        String token = service.createAccessToken(userId, List.of("ROLE_CODE", "PERMISSION_CODE", "ROLE_CODE"));

        var claims = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("typ")).isEqualTo("access");
        assertThat(claims.get("authorities", List.class)).containsExactly("PERMISSION_CODE", "ROLE_CODE");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims).doesNotContainKeys("password", "passwordHash", "refreshToken", "email", "phone", "address");
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JjwtService shortLived = serviceWithExpiration(Duration.ofMillis(1));
        String token = shortLived.createAccessToken(UUID.randomUUID(), List.of());
        Thread.sleep(10);
        assertThatThrownBy(() -> shortLived.parseAccessToken(token)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsTokenWhoseTypeIsNotAccess() {
        String token = Jwts.builder().subject(UUID.randomUUID().toString()).claim("typ", "refresh")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parseAccessToken(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialPasswordChangeTokenIsShortLivedAndNeverParsesAsAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = service.createInitialPasswordChangeToken(userId);

        assertThat(service.parseInitialPasswordChangeToken(token)).isEqualTo(userId);
        assertThatThrownBy(() -> service.parseAccessToken(token)).isInstanceOf(IllegalArgumentException.class);
        var claims = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("typ")).isEqualTo("initial_password_change");
        assertThat(claims.get("authorities")).isNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(600_000L);
    }

    @Test
    void rejectsExpiredInitialPasswordChangeToken() {
        JwtProperties properties = new JwtProperties(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofHours(24), false);
        JjwtService expiredService = new JjwtService(properties,
                Clock.fixed(Instant.now().minus(Duration.ofMinutes(20)), ZoneOffset.UTC));
        String expiredToken = expiredService.createInitialPasswordChangeToken(UUID.randomUUID());

        assertThatThrownBy(() -> expiredService.parseInitialPasswordChangeToken(expiredToken))
                .isInstanceOf(RuntimeException.class);
    }

    private JjwtService serviceWithExpiration(Duration expiration) {
        return new JjwtService(new JwtProperties(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                expiration, Duration.ofDays(7), Duration.ofHours(24), false));
    }
}
