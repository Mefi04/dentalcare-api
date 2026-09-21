package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final String AUTH_REQUIRED = "Authentication is required";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthUserMapper mapper;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthUserMapper mapper,
                           RefreshTokenService refreshTokenService,
                           JwtProperties jwtProperties,
                           Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthUserMapper mapper,
                           RefreshTokenService refreshTokenService,
                           JwtProperties jwtProperties) {
        this(userRepository, passwordEncoder, jwtService, mapper, refreshTokenService, jwtProperties, Clock.systemUTC());
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request) {
        String identifier = User.normalize(request.identifier());
        User user = userRepository.findWithRolesAndPermissionsByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        user.setLastLoginAt(clock.instant());
        userRepository.save(user);

        String accessToken = jwtService.createAccessToken(user.getId(), mapper.authorities(user));
        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenLifetimeSeconds(),
                mapper.toResponse(user)
        );

        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = refreshTokenService.generateRawToken();
        Duration refreshExpiration = jwtProperties.refreshExpiration() != null
                ? jwtProperties.refreshExpiration()
                : Duration.ofDays(7);
        Instant expiresAt = clock.instant().plus(refreshExpiration);

        refreshTokenService.createSession(user, familyId, rawRefreshToken, expiresAt);

        return new LoginResult(response, rawRefreshToken, refreshExpiration);
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException(AUTH_REQUIRED);
        }

        RefreshSession session = refreshTokenService.findByRawToken(rawRefreshToken)
                .orElseThrow(() -> new UnauthorizedException(AUTH_REQUIRED));

        // Reuse detection: a previously rotated token was presented
        if (session.getReplacedBySession() != null) {
            refreshTokenService.revokeFamily(session.getFamilyId());
            throw new UnauthorizedException(AUTH_REQUIRED);
        }

        // Revocation check
        if (session.getRevokedAt() != null) {
            throw new UnauthorizedException(AUTH_REQUIRED);
        }

        Instant now = clock.instant();

        // Absolute expiration check
        if (!session.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException(AUTH_REQUIRED);
        }

        // Inactivity timeout check
        Duration inactivityTimeout = jwtProperties.refreshInactivityTimeout() != null
                ? jwtProperties.refreshInactivityTimeout()
                : Duration.ofHours(24);
        Instant inactivityDeadline = session.getLastActivityAt().plus(inactivityTimeout);
        if (!inactivityDeadline.isAfter(now)) {
            throw new UnauthorizedException(AUTH_REQUIRED);
        }

        // Account eligibility check: user must exist and remain ACTIVE
        User user = userRepository.findWithRolesAndPermissionsById(session.getUser().getId())
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException(AUTH_REQUIRED));

        // Rotate session: atomic revocation of old and persistence of new in same family
        String newRawRefreshToken = refreshTokenService.generateRawToken();
        RefreshSession newSession = refreshTokenService.rotateSession(session, newRawRefreshToken);

        // Issue new access token with up-to-date user authorities
        String newAccessToken = jwtService.createAccessToken(user.getId(), mapper.authorities(user));
        RefreshResponse response = new RefreshResponse(
                newAccessToken,
                "Bearer",
                jwtService.getAccessTokenLifetimeSeconds()
        );

        Duration remainingDuration = Duration.between(now, newSession.getExpiresAt());
        if (remainingDuration.isNegative() || remainingDuration.isZero()) {
            remainingDuration = Duration.ofSeconds(1);
        }

        return new RefreshResult(response, newRawRefreshToken, remainingDuration);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenService.findByRawToken(rawRefreshToken)
                .ifPresent(session -> {
                    if (session.getRevokedAt() == null) {
                        refreshTokenService.revokeSession(session);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findWithRolesAndPermissionsById(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException(AUTH_REQUIRED));
        return mapper.toResponse(user);
    }
}
