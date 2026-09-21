package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.auth.repository.RefreshSessionRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTests {

    @Mock
    private RefreshSessionRepository repository;

    private Clock fixedClock;
    private Instant now;
    private RefreshTokenServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-21T12:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        service = new RefreshTokenServiceImpl(repository, fixedClock);
        user = new User(UUID.randomUUID(), "testuser", "test@example.com", "hash",
                UserStatus.ACTIVE, now, now);
    }

    @Test
    void generatesOpaqueCryptographicallySecureRandomTokens() {
        String token1 = service.generateRawToken();
        String token2 = service.generateRawToken();

        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).doesNotContain(" ");
        assertThat(token1.length()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void hashesDeterministicallyWithSha256InHex() {
        String token = "sample-secure-token-123456";
        String hash1 = service.hashToken(token);
        String hash2 = service.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[a-f0-9]{64}$");
    }

    @Test
    void rejectsNullOrBlankTokenHashing() {
        assertThatThrownBy(() -> service.hashToken(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.hashToken("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsSessionWithGivenUserFamilyAndSha256Hash() {
        UUID familyId = UUID.randomUUID();
        String rawToken = service.generateRawToken();
        Instant expiresAt = now.plus(Duration.ofDays(7));

        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshSession session = service.createSession(user, familyId, rawToken, expiresAt);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getUser()).isEqualTo(user);
        assertThat(session.getFamilyId()).isEqualTo(familyId);
        assertThat(session.getTokenHash()).isEqualTo(service.hashToken(rawToken));
        assertThat(session.getCreatedAt()).isEqualTo(now);
        assertThat(session.getLastActivityAt()).isEqualTo(now);
        assertThat(session.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getReplacedBySession()).isNull();

        verify(repository).save(session);
    }

    @Test
    void rotatesSessionPreservingOriginalExpiresAtAndLinkingReplacement() {
        UUID familyId = UUID.randomUUID();
        String rawOld = "old-raw-token";
        Instant originalExpiresAt = now.plus(Duration.ofDays(7));

        RefreshSession oldSession = new RefreshSession(
                UUID.randomUUID(), user, familyId, service.hashToken(rawOld),
                now.minus(Duration.ofDays(1)), originalExpiresAt, now.minus(Duration.ofHours(2))
        );

        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String newRawToken = "new-raw-token";
        RefreshSession newSession = service.rotateSession(oldSession, newRawToken);

        assertThat(newSession.getId()).isNotNull();
        assertThat(newSession.getFamilyId()).isEqualTo(familyId);
        assertThat(newSession.getTokenHash()).isEqualTo(service.hashToken(newRawToken));
        assertThat(newSession.getExpiresAt()).isEqualTo(originalExpiresAt);
        assertThat(newSession.getCreatedAt()).isEqualTo(now);
        assertThat(newSession.getLastActivityAt()).isEqualTo(now);
        assertThat(newSession.getRevokedAt()).isNull();

        assertThat(oldSession.getRevokedAt()).isEqualTo(now);
        assertThat(oldSession.getReplacedBySession()).isEqualTo(newSession);

        verify(repository, times(2)).save(any(RefreshSession.class));
    }

    @Test
    void revokesAllActiveSessionsInFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session1 = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash1", now, now.plus(Duration.ofDays(7)), now);
        RefreshSession session2 = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash2", now, now.plus(Duration.ofDays(7)), now);
        session2.setRevokedAt(now.minus(Duration.ofHours(1))); // already revoked

        when(repository.findAllByFamilyId(familyId)).thenReturn(List.of(session1, session2));

        service.revokeFamily(familyId);

        assertThat(session1.getRevokedAt()).isEqualTo(now);
        assertThat(session2.getRevokedAt()).isEqualTo(now.minus(Duration.ofHours(1))); // untouched

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RefreshSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(session1);
    }

    @Test
    void revokesSingleSession() {
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, UUID.randomUUID(), "hash", now, now.plus(Duration.ofDays(7)), now);

        service.revokeSession(session);

        assertThat(session.getRevokedAt()).isEqualTo(now);
        verify(repository).save(session);
    }

    @Test
    void findsSessionByRawTokenUsingDeterministicHash() {
        String raw = "my-test-token";
        String expectedHash = service.hashToken(raw);
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, UUID.randomUUID(), expectedHash, now, now.plus(Duration.ofDays(7)), now);

        when(repository.findByTokenHash(expectedHash)).thenReturn(Optional.of(session));

        Optional<RefreshSession> result = service.findByRawToken(raw);

        assertThat(result).contains(session);
        verify(repository).findByTokenHash(expectedHash);
    }

    @Test
    void findByRawTokenReturnsEmptyForNullOrBlank() {
        assertThat(service.findByRawToken(null)).isEmpty();
        assertThat(service.findByRawToken("   ")).isEmpty();
        verifyNoInteractions(repository);
    }
}
