package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.users.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {

    String generateRawToken();

    String hashToken(String rawToken);

    RefreshSession createSession(User user, UUID familyId, String rawToken, Instant expiresAt);

    RefreshSession rotateSession(RefreshSession currentSession, String newRawToken);

    void revokeFamily(UUID familyId);

    void revokeSession(RefreshSession session);

    Optional<RefreshSession> findByRawToken(String rawToken);
}
