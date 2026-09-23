package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.ActivateAccountRequest;
import com.dentalcare.api.modules.auth.dto.response.ActivateAccountResponse;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.users.model.Permission;
import com.dentalcare.api.modules.users.model.Role;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private Clock clock;
    private Instant now;
    private JwtProperties jwtProperties;
    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-21T12:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
        jwtProperties = new JwtProperties(
                "privKey",
                "pubKey",
                Duration.ofMinutes(30),
                Duration.ofDays(7),
                Duration.ofHours(24),
                false
        );

        service = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtService,
                new AuthUserMapper(),
                refreshTokenService,
                jwtProperties,
                clock
        );

        user = new User(UUID.randomUUID(), "testuser", "user@example.com", "1234567890123", "hash", UserStatus.ACTIVE,
                now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(10)));
    }

    @Test
    void loginWithCuiCreatesSessionAndReturnsLoginResult() {
        successfulLoginLookup();
        when(refreshTokenService.generateRawToken()).thenReturn("raw-refresh-token");

        var result = service.login(new LoginRequest("1234567890123", "secret"));

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().expiresIn()).isEqualTo(1800);
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(result.cookieMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(user.getLastLoginAt()).isEqualTo(now);

        verify(userRepository).findWithRolesAndPermissionsByCui("1234567890123");
        verify(userRepository).save(user);
        verify(refreshTokenService).createSession(eq(user), any(UUID.class), eq("raw-refresh-token"), eq(now.plus(Duration.ofDays(7))));
    }

    @Test
    void loginTrimsCuiBeforeLookup() {
        successfulLoginLookup();
        when(refreshTokenService.generateRawToken()).thenReturn("raw-refresh-token");

        service.login(new LoginRequest(" 1234567890123 ", "secret"));

        verify(userRepository).findWithRolesAndPermissionsByCui("1234567890123");
    }

    @Test
    void invalidCredentialsDoNotCreateSession() {
        when(userRepository.findWithRolesAndPermissionsByCui("0000000000000"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("0000000000000", "secret")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

        when(userRepository.findWithRolesAndPermissionsByCui("1234567890123"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("1234567890123", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

        verify(refreshTokenService, never()).createSession(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonActiveUserCannotLogin() {
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findWithRolesAndPermissionsByCui("1234567890123"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("1234567890123", "secret")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

        verifyNoInteractions(passwordEncoder);
        verify(refreshTokenService, never()).createSession(any(), any(), any(), any());
    }

    @Test
    void refreshRejectsNullOrBlankToken() {
        assertThatThrownBy(() -> service.refresh(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        assertThatThrownBy(() -> service.refresh("  "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void refreshRejectsNonExistentToken() {
        when(refreshTokenService.findByRawToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("unknown"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void refreshDetectsReuseAndRevokesEntireFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)), now.minus(Duration.ofHours(1)));
        session.setReplacedBySession(new RefreshSession()); // already rotated!

        when(refreshTokenService.findByRawToken("stolen-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh("stolen-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        verify(refreshTokenService).revokeFamily(familyId);
        verify(refreshTokenService, never()).rotateSession(any(), any());
    }

    @Test
    void refreshRejectsAlreadyRevokedSession() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)), now.minus(Duration.ofHours(1)));
        session.setRevokedAt(now.minus(Duration.ofHours(2)));

        when(refreshTokenService.findByRawToken("revoked-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh("revoked-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        verify(refreshTokenService, never()).rotateSession(any(), any());
    }

    @Test
    void refreshRejectsAbsolutelyExpiredSession() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash", now.minus(Duration.ofDays(8)),
                now.minus(Duration.ofSeconds(1)), now.minus(Duration.ofHours(1)));

        when(refreshTokenService.findByRawToken("expired-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh("expired-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        verify(refreshTokenService, never()).rotateSession(any(), any());
    }

    @Test
    void refreshRejectsInactiveSessionAfter24Hours() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash", now.minus(Duration.ofDays(2)),
                now.plus(Duration.ofDays(5)), now.minus(Duration.ofHours(24).plusSeconds(1)));

        when(refreshTokenService.findByRawToken("inactive-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh("inactive-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        verify(refreshTokenService, never()).rotateSession(any(), any());
    }

    @Test
    void refreshRejectsIfUserIsNoLongerActive() {
        UUID familyId = UUID.randomUUID();
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "hash", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)), now.minus(Duration.ofHours(2)));

        when(refreshTokenService.findByRawToken("valid-token")).thenReturn(Optional.of(session));
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refresh("valid-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");

        verify(refreshTokenService, never()).rotateSession(any(), any());
    }

    @Test
    void refreshRotatesSessionAndIssuesNewAccessToken() {
        UUID familyId = UUID.randomUUID();
        Instant originalExpiresAt = now.plus(Duration.ofDays(4));
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, familyId, "old-hash", now.minus(Duration.ofDays(3)),
                originalExpiresAt, now.minus(Duration.ofHours(2)));

        when(refreshTokenService.findByRawToken("valid-token")).thenReturn(Optional.of(session));
        when(userRepository.findWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenService.generateRawToken()).thenReturn("new-raw-token");
        Role administrator = new Role(UUID.randomUUID(), "ADMINISTRATOR", "Administrator", null, true);
        administrator.setPermissions(Set.of(new Permission(UUID.randomUUID(), "PATIENT_READ", null)));
        user.setRoles(Set.of(administrator));

        RefreshSession newSession = new RefreshSession(
                UUID.randomUUID(), user, familyId, "new-hash", now, originalExpiresAt, now);
        when(refreshTokenService.rotateSession(session, "new-raw-token")).thenReturn(newSession);

        when(jwtService.createAccessToken(eq(user.getId()), anyList())).thenReturn("new-access-token");
        when(jwtService.getAccessTokenLifetimeSeconds()).thenReturn(1800L);

        var result = service.refresh("valid-token");

        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(1800L);
        assertThat(result.refreshToken()).isEqualTo("new-raw-token");
        assertThat(result.cookieMaxAge()).isEqualTo(Duration.ofDays(4));

        verify(refreshTokenService).rotateSession(session, "new-raw-token");
        verify(jwtService).createAccessToken(user.getId(), List.of("PATIENT_READ", "ROLE_ADMINISTRATOR"));
    }

    @Test
    void logoutRevokesActiveSessionIdempotently() {
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, UUID.randomUUID(), "hash", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)), now);

        when(refreshTokenService.findByRawToken("my-token")).thenReturn(Optional.of(session));

        service.logout("my-token");

        verify(refreshTokenService).revokeSession(session);
    }

    @Test
    void logoutWithNullBlankOrNonExistentTokenDoesNotThrow() {
        service.logout(null);
        service.logout("   ");

        when(refreshTokenService.findByRawToken("nonexistent")).thenReturn(Optional.empty());
        service.logout("nonexistent");

        verify(refreshTokenService, never()).revokeSession(any());
    }

    @Test
    void logoutWithAlreadyRevokedSessionDoesNotRevokeAgain() {
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(), user, UUID.randomUUID(), "hash", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(6)), now);
        session.setRevokedAt(now.minus(Duration.ofMinutes(10)));

        when(refreshTokenService.findByRawToken("already-revoked")).thenReturn(Optional.of(session));

        service.logout("already-revoked");

        verify(refreshTokenService, never()).revokeSession(any());
    }

    @Test
    void getCurrentUserReturnsUserResponseForActiveUser() {
        when(userRepository.findWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = service.getCurrentUser(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.username()).isEqualTo("testuser");
    }

    @Test
    void getCurrentUserThrowsWhenUserNotFoundOrInactive() {
        UUID id = UUID.randomUUID();
        when(userRepository.findWithRolesAndPermissionsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(id))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    private void successfulLoginLookup() {
        when(userRepository.findWithRolesAndPermissionsByCui(anyString()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.createAccessToken(eq(user.getId()), anyList())).thenReturn("access-token");
        when(jwtService.getAccessTokenLifetimeSeconds()).thenReturn(1800L);
    }

    @Test
    void activatePendingUserWithValidCredentialsSuccessfullyActivatesAccount() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setPasswordHash("temporary-hash");
        when(userRepository.findByCuiForUpdate("1234567890123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("TempSecret123!", "temporary-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPermanentPassword123!")).thenReturn("new-bcrypt-hash");

        ActivateAccountResponse response = service.activate(
                new ActivateAccountRequest("1234567890123", "TempSecret123!", "NewPermanentPassword123!"));

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.message()).isEqualTo("Account activated successfully");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("new-bcrypt-hash");
        assertThat(user.getUpdatedAt()).isEqualTo(now);

        verify(userRepository).findByCuiForUpdate("1234567890123");
        verify(passwordEncoder).encode("NewPermanentPassword123!");
        verify(userRepository).save(user);
    }

    @Test
    void activateTrimsCuiBeforeLookup() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setPasswordHash("temporary-hash");
        when(userRepository.findByCuiForUpdate("1234567890123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("TempSecret123!", "temporary-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPermanentPassword123!")).thenReturn("new-bcrypt-hash");

        service.activate(new ActivateAccountRequest("  1234567890123  ", "TempSecret123!", "NewPermanentPassword123!"));

        verify(userRepository).findByCuiForUpdate("1234567890123");
    }

    @Test
    void activateWithIncorrectTemporaryPasswordThrowsUnauthorizedAndDoesNotModifyUser() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setPasswordHash("temporary-hash");
        when(userRepository.findByCuiForUpdate("1234567890123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongTempPassword", "temporary-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.activate(
                new ActivateAccountRequest("1234567890123", "WrongTempPassword", "NewPermanentPassword123!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid activation credentials");

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateNonExistentUserThrowsGenericUnauthorizedWithoutDisclosingAccountAbsence() {
        when(userRepository.findByCuiForUpdate("9999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(
                new ActivateAccountRequest("9999999999999", "TempSecret123!", "NewPermanentPassword123!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid activation credentials");

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateRejectsNonPendingStatusesWithoutModifyingUser() {
        for (UserStatus nonPendingStatus : List.of(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.LOCKED)) {
            user.setStatus(nonPendingStatus);
            user.setPasswordHash("original-hash");
            when(userRepository.findByCuiForUpdate("1234567890123")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.activate(
                    new ActivateAccountRequest("1234567890123", "TempSecret123!", "NewPermanentPassword123!")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid activation credentials");

            assertThat(user.getStatus()).isEqualTo(nonPendingStatus);
            assertThat(user.getPasswordHash()).isEqualTo("original-hash");
        }
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateRejectsRepeatedActivationAttempts() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setPasswordHash("temporary-hash");
        when(userRepository.findByCuiForUpdate("1234567890123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("TempSecret123!", "temporary-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPermanentPassword123!")).thenReturn("new-bcrypt-hash");

        service.activate(new ActivateAccountRequest("1234567890123", "TempSecret123!", "NewPermanentPassword123!"));
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.activate(
                new ActivateAccountRequest("1234567890123", "TempSecret123!", "AnotherPassword123!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid activation credentials");
    }

    @Test
    void activateWithInvalidNewPasswordThrowsBadRequestAndDoesNotQueryRepository() {
        assertThatThrownBy(() -> service.activate(
                new ActivateAccountRequest("1234567890123", "TempSecret123!", "short")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New password must be between 8 and 128 characters");

        assertThatThrownBy(() -> service.activate(
                new ActivateAccountRequest("1234567890123", "TempSecret123!", "   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New password is required");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }
}
