package com.dentalcare.api.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JjwtService implements JwtService {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Duration accessExpiration;
    private final Clock clock;

    public JjwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JjwtService(JwtProperties properties, Clock clock) {
        this.privateKey = parsePrivateKey(properties.privateKey());
        this.publicKey = parsePublicKey(properties.publicKey());
        this.accessExpiration = properties.accessExpiration() != null ? properties.accessExpiration() : Duration.ofMinutes(30);
        this.clock = clock;
    }

    @Override
    public String createAccessToken(UUID userId, List<String> authorities) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("authorities", authorities.stream().distinct().sorted().toList())
                .claim("typ", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessExpiration)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Token is not an access token");
        }
        List<?> rawAuthorities = claims.get("authorities", List.class);
        List<String> authorities = rawAuthorities == null ? List.of() : rawAuthorities.stream()
                .filter(String.class::isInstance).map(String.class::cast).distinct().sorted().toList();
        return new AccessTokenClaims(UUID.fromString(claims.getSubject()), authorities);
    }

    @Override
    public long getAccessTokenLifetimeSeconds() {
        return accessExpiration.toSeconds();
    }

    private static PrivateKey parsePrivateKey(String value) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(value, "PRIVATE KEY")));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT private key configuration is invalid", exception);
        }
    }

    private static PublicKey parsePublicKey(String value) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(value, "PUBLIC KEY")));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key configuration is invalid", exception);
        }
    }

    private static byte[] decodePem(String value, String type) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JWT " + type.toLowerCase() + " is required");
        }
        String normalized = value.replace("\\n", "\n")
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
