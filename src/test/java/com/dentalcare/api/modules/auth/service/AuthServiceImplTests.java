package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTests {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, new AuthUserMapper());
        user = new User(UUID.randomUUID(), "testuser", "user@example.com", "hash", UserStatus.ACTIVE,
                Instant.now(), Instant.now());
    }

    @Test
    void logsInWithNormalizedUsernameAndUpdatesLastLogin() {
        successfulLookup();
        var response = service.login(new LoginRequest("  TESTUSER ", "secret"));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).findWithRolesAndPermissionsByUsernameOrEmail("testuser", "testuser");
        verify(userRepository).save(user);
    }

    @Test
    void logsInWithNormalizedEmail() {
        successfulLookup();
        service.login(new LoginRequest(" USER@EXAMPLE.COM ", "secret"));
        verify(userRepository).findWithRolesAndPermissionsByUsernameOrEmail("user@example.com", "user@example.com");
    }

    @Test
    void invalidIdentifierOrPasswordReturnsSameUnauthorizedFailure() {
        when(userRepository.findWithRolesAndPermissionsByUsernameOrEmail("missing", "missing"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "secret")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");

        when(userRepository.findWithRolesAndPermissionsByUsernameOrEmail("testuser", "testuser"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("testuser", "wrong")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonActiveUserCannotAuthenticate() {
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findWithRolesAndPermissionsByUsernameOrEmail("testuser", "testuser"))
                .thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login(new LoginRequest("testuser", "secret")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    private void successfulLookup() {
        when(userRepository.findWithRolesAndPermissionsByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.createAccessToken(eq(user.getId()), anyList())).thenReturn("access-token");
        when(jwtService.getAccessTokenLifetimeSeconds()).thenReturn(1800L);
    }
}
