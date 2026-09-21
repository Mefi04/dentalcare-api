package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.auth.repository.RefreshSessionRepository;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshSessionRepository refreshSessionRepository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenServiceImpl(RefreshSessionRepository refreshSessionRepository, Clock clock) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    public RefreshTokenServiceImpl(RefreshSessionRepository refreshSessionRepository) {
        this(refreshSessionRepository, Clock.systemUTC());
    }

    @Override
    public String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Raw token must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    @Transactional
    public RefreshSession createSession(User user, UUID familyId, String rawToken, Instant expiresAt) {
        UUID sessionId = UUID.randomUUID();
        String tokenHash = hashToken(rawToken);
        Instant now = clock.instant();

        RefreshSession session = new RefreshSession(
                sessionId,
                user,
                familyId,
                tokenHash,
                now,
                expiresAt,
                now
        );
        return refreshSessionRepository.save(session);
    }

    @Override
    @Transactional
    public RefreshSession rotateSession(RefreshSession currentSession, String newRawToken) {
        Instant now = clock.instant();
        String newHash = hashToken(newRawToken);

        RefreshSession newSession = new RefreshSession(
                UUID.randomUUID(),
                currentSession.getUser(),
                currentSession.getFamilyId(),
                newHash,
                now,
                currentSession.getExpiresAt(),
                now
        );
        RefreshSession savedNewSession = refreshSessionRepository.save(newSession);

        currentSession.setRevokedAt(now);
        currentSession.setReplacedBySession(savedNewSession);
        refreshSessionRepository.save(currentSession);

        return savedNewSession;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllByFamilyId(familyId);
        Instant now = clock.instant();
        List<RefreshSession> toUpdate = new ArrayList<>();
        for (RefreshSession session : sessions) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                toUpdate.add(session);
            }
        }
        if (!toUpdate.isEmpty()) {
            refreshSessionRepository.saveAll(toUpdate);
        }
    }

    @Override
    @Transactional
    public void revokeSession(RefreshSession session) {
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(clock.instant());
            refreshSessionRepository.save(session);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshSession> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(rawToken);
        return refreshSessionRepository.findByTokenHash(tokenHash);
    }
}
