package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.auth.dto.request.ChangePasswordRequest;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticatedPasswordChangeServiceTests {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuditService auditService;
    private AuthServiceImpl service;
    private User user;
    private UUID userId;
    private final Instant now = Instant.parse("2026-10-05T12:00:00Z");

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User(userId, "patient", "patient@example.com", "1234567890123", "old-hash",
                UserStatus.ACTIVE, now.minusSeconds(10), now.minusSeconds(10));
        service = new AuthServiceImpl(users, encoder, jwt, new AuthUserMapper(), refreshTokens,
                new JwtProperties("private", "public", Duration.ofMinutes(30), Duration.ofDays(7),
                        Duration.ofHours(24), false), Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "auditService", auditService);
    }

    @Test
    void changesPasswordAndRevokesEveryRefreshSession() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("CurrentPassword123!", "old-hash")).thenReturn(true);
        when(encoder.encode("NewPassword456!")).thenReturn("new-hash");

        service.changePassword(userId, new ChangePasswordRequest("CurrentPassword123!", "NewPassword456!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getUpdatedAt()).isEqualTo(now);
        verify(users).save(user);
        verify(refreshTokens).revokeAllForUser(userId);
        verify(auditService).success(AuditActions.AUTH_PASSWORD_CHANGED, "AUTH", "User", userId, userId);
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutChangingOrRevoking() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(encoder.matches("WrongPassword", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(userId,
                new ChangePasswordRequest("WrongPassword", "NewPassword456!")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(users, never()).save(any());
        verifyNoInteractions(refreshTokens);
        verify(auditService, never()).success(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void rejectsInvalidNewPasswordBeforeAccessingUser() {
        assertThatThrownBy(() -> service.changePassword(userId,
                new ChangePasswordRequest("CurrentPassword123!", "short")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New password must be between 8 and 128 characters");
        verifyNoInteractions(users, encoder, refreshTokens);
    }

    @Test
    void rejectsMissingOrInactiveAuthenticatedUser() {
        when(users.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword(userId,
                new ChangePasswordRequest("CurrentPassword123!", "NewPassword456!")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Authentication is required");
        verifyNoInteractions(encoder, refreshTokens);
    }
}
