package com.dentalcare.api.security.ratelimit;

import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);
    private final DatabaseRateLimitRepository repository;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitProperties properties;
    private final AuditService auditService;

    public RateLimitService(DatabaseRateLimitRepository repository, ClientIpResolver clientIpResolver,
                            RateLimitProperties properties, ObjectProvider<AuditService> auditService) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
        this.auditService = auditService.getIfAvailable();
    }

    public void checkIp(RateLimitPolicy policy, HttpServletRequest request) {
        if (!properties.isEnabled()) return;
        RateLimitProperties.Rule rule = properties.rule(policy);
        enforce(policy, "ip", clientIpResolver.resolve(request), rule.getIpLimit(), rule);
    }

    public void checkIdentity(RateLimitPolicy policy, String identity) {
        if (!properties.isEnabled() || identity == null || identity.isBlank()) return;
        RateLimitProperties.Rule rule = properties.rule(policy);
        if (rule.getIdentityLimit() == 0) return;
        enforce(policy, "identity", identity.trim().toLowerCase(Locale.ROOT), rule.getIdentityLimit(), rule);
    }

    private void enforce(RateLimitPolicy policy, String dimension, String value, int limit,
                         RateLimitProperties.Rule rule) {
        String bucketKey = policy.name() + ':' + dimension + ':' + sha256(value);
        RateLimitDecision decision = repository.consume(bucketKey, limit, rule.getWindow());
        if (!decision.allowed()) {
            recordBlocked(policy);
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    private void recordBlocked(RateLimitPolicy policy) {
        if (auditService == null) return;
        try {
            auditService.failure(AuditActions.SECURITY_RATE_LIMIT_BLOCKED, "SECURITY", policy.name(), null, null);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not persist rate-limit audit event for policy {}", policy, exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
