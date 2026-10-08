package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.auth.service.AuthServiceImpl;
import com.dentalcare.api.modules.auth.service.RefreshTokenService;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.cookie.AuthCookieManager;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MobileAuthController.class, AuthController.class}, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, AuthCookieManager.class, AuthServiceImpl.class, AuthUserMapper.class,
        GlobalExceptionHandler.class, MobileAuthFlowIntegrationTests.TestConfig.class})
class MobileAuthFlowIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final String CUI = "1234567890123";
    private static final String PASSWORD = "SecurePassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private com.dentalcare.api.security.ratelimit.RateLimitService rateLimitService;

    private User user;
    private final ConcurrentHashMap<String, RefreshSession> sessionsByToken = new ConcurrentHashMap<>();

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void setUp() {
        sessionsByToken.clear();

        when(jwtProperties.cookieSecure()).thenReturn(false);
        when(jwtProperties.refreshExpiration()).thenReturn(Duration.ofDays(7));
        when(jwtProperties.refreshInactivityTimeout()).thenReturn(Duration.ofHours(24));
        when(jwtService.getAccessTokenLifetimeSeconds()).thenReturn(1800L);

        user = new User(
                UUID.randomUUID(),
                "mobileuser",
                "Mobile User",
                "mobile@example.com",
                CUI,
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(1))
        );

        when(userRepository.findWithRolesAndPermissionsByCui(CUI)).thenAnswer(inv -> Optional.of(user));
        when(userRepository.findWithRolesAndPermissionsById(user.getId())).thenAnswer(inv -> Optional.of(user));
        when(userRepository.findById(user.getId())).thenAnswer(inv -> Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        when(jwtService.createAccessToken(any(UUID.class), anyList())).thenAnswer(inv -> "jwt-" + UUID.randomUUID());

        // Simulated RefreshTokenService behavior using sessionsByToken
        when(refreshTokenService.generateRawToken()).thenAnswer(inv -> "raw-token-" + UUID.randomUUID());

        when(refreshTokenService.createSession(any(User.class), any(UUID.class), any(String.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    User u = inv.getArgument(0);
                    UUID familyId = inv.getArgument(1);
                    String rawToken = inv.getArgument(2);
                    Instant exp = inv.getArgument(3);
                    RefreshSession s = new RefreshSession(UUID.randomUUID(), u, familyId, "hash-" + rawToken, NOW, exp, NOW);
                    sessionsByToken.put(rawToken, s);
                    return s;
                });

        when(refreshTokenService.findByRawTokenForUpdate(any(String.class)))
                .thenAnswer(inv -> {
                    String token = inv.getArgument(0);
                    return Optional.ofNullable(sessionsByToken.get(token));
                });
        when(refreshTokenService.findByRawToken(any(String.class)))
                .thenAnswer(inv -> {
                    String token = inv.getArgument(0);
                    return Optional.ofNullable(sessionsByToken.get(token));
                });

        when(refreshTokenService.rotateSession(any(RefreshSession.class), any(String.class)))
                .thenAnswer(inv -> {
                    RefreshSession current = inv.getArgument(0);
                    String newRaw = inv.getArgument(1);
                    RefreshSession next = new RefreshSession(
                            UUID.randomUUID(), current.getUser(), current.getFamilyId(), "hash-" + newRaw, NOW, current.getExpiresAt(), NOW);
                    current.setRevokedAt(NOW);
                    current.setReplacedBySession(next);
                    sessionsByToken.put(newRaw, next);
                    return next;
                });

        doAnswer(inv -> {
            RefreshSession s = inv.getArgument(0);
            s.setRevokedAt(NOW);
            return null;
        }).when(refreshTokenService).revokeSession(any(RefreshSession.class));

        doAnswer(inv -> {
            UUID fId = inv.getArgument(0);
            sessionsByToken.values().stream()
                    .filter(s -> s.getFamilyId().equals(fId))
                    .forEach(s -> s.setRevokedAt(NOW));
            return null;
        }).when(refreshTokenService).revokeFamily(any(UUID.class));
    }

    @Test
    void completeMobileAuthLifecycleWithRotationReuseDetectionAndRevocation() throws Exception {
        // 1. Mobile Login
        String loginResponseContent = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.username").value("mobileuser"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();

        com.jayway.jsonpath.ReadContext ctx = com.jayway.jsonpath.JsonPath.parse(loginResponseContent);
        String refreshToken1 = ctx.read("$.refreshToken");

        // 2. Mobile Refresh (Token Rotation)
        String refreshResponseContent = mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();

        com.jayway.jsonpath.ReadContext refreshCtx = com.jayway.jsonpath.JsonPath.parse(refreshResponseContent);
        String refreshToken2 = refreshCtx.read("$.refreshToken");

        org.assertj.core.api.Assertions.assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // 3. Token Reuse Detection: presenting old refreshToken1 again must revoke entire family and return 401
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // 4. Verifying family revocation: refreshToken2 must now also fail with 401
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void changedPasswordRejectsPreviousPasswordAndAllowsNewPassword() throws Exception {
        String newPassword = "NewSecurePassword456!";
        when(jwtService.parseAccessToken("patient-access")).thenReturn(
                new JwtService.AccessTokenClaims(user.getId(), java.util.List.of("ROLE_PATIENT")));

        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}",
                                PASSWORD, newPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void mobileLogoutRevokesSessionAndSubsequentRefreshFails() throws Exception {
        // 1. Mobile Login
        String loginResponseContent = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponseContent, "$.refreshToken");

        // 2. Mobile Logout
        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // 3. Subsequent Refresh with revoked token returns 401
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void webAndMobileContractsRemainStrictlyIsolated() throws Exception {
        // Web login: Sets HttpOnly cookie, does NOT have refreshToken in JSON
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));

        // Mobile login: Returns refreshToken in JSON, does NOT set cookie
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void pendingActivationUserCannotLoginViaMobile() throws Exception {
        user.setStatus(UserStatus.PENDING_ACTIVATION);

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void inactiveUserCannotLoginViaMobile() throws Exception {
        user.setStatus(UserStatus.INACTIVE);

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void userDeactivatedAfterLoginCannotRefreshViaMobile() throws Exception {
        // 1. Mobile login while active
        String loginResponse = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");

        // 2. User gets deactivated
        user.setStatus(UserStatus.INACTIVE);

        // 3. Attempting refresh fails with 401
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void inactiveSessionCannotRefreshViaMobile() throws Exception {
        // Create an inactive session (> 24 hours without activity)
        String inactiveToken = "token-inactive-session";
        RefreshSession session = new RefreshSession(
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                "hash-" + inactiveToken,
                NOW.minus(Duration.ofDays(2)),
                NOW.plus(Duration.ofDays(5)),
                NOW.minus(Duration.ofHours(25)) // 25 hours inactive
        );
        sessionsByToken.put(inactiveToken, session);

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", inactiveToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
