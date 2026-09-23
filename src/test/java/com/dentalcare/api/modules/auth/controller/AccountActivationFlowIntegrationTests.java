package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, AuthCookieManager.class, AuthServiceImpl.class, AuthUserMapper.class,
        GlobalExceptionHandler.class, AccountActivationFlowIntegrationTests.TestConfig.class})
class AccountActivationFlowIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final String CUI = "1234567890123";
    private static final String TEMPORARY_PASSWORD = "InitialTemporaryPass123!";
    private static final String NEW_PASSWORD = "PermanentSecurePass456!";

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

    private User user;

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void setUp() {
        when(jwtProperties.cookieSecure()).thenReturn(false);
        when(jwtProperties.refreshExpiration()).thenReturn(Duration.ofDays(7));
        when(jwtService.createAccessToken(any(UUID.class), anyList())).thenReturn("mock-access-token");
        when(jwtService.getAccessTokenLifetimeSeconds()).thenReturn(1800L);
        when(refreshTokenService.generateRawToken()).thenReturn("mock-refresh-token");

        user = new User(
                UUID.randomUUID(),
                "staff-test-user",
                "Staff Member",
                "staff@example.com",
                CUI,
                passwordEncoder.encode(TEMPORARY_PASSWORD),
                UserStatus.PENDING_ACTIVATION,
                NOW.minus(Duration.ofDays(1)),
                NOW.minus(Duration.ofDays(1))
        );

        when(userRepository.findByCuiForUpdate(CUI)).thenAnswer(inv -> Optional.of(user));
        when(userRepository.findWithRolesAndPermissionsByCui(CUI)).thenAnswer(inv -> Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void completeAccountActivationAndSubsequentLoginFlow() throws Exception {
        // Step 1: Initial state verification
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(passwordEncoder.matches(TEMPORARY_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isFalse();

        // Step 2: Attempting login before activation fails with 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, TEMPORARY_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Step 3: Perform POST /api/v1/auth/activate with temporary password and new password
        mockMvc.perform(post("/api/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "cui": "%s",
                                  "temporaryPassword": "%s",
                                  "newPassword": "%s"
                                }
                                """, CUI, TEMPORARY_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Account activated successfully"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // Step 4: Verify post-activation entity state
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(TEMPORARY_PASSWORD, user.getPasswordHash())).isFalse();

        // Step 5: Successful login with new permanent password
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=mock-refresh-token")));

        // Step 6: Login with old temporary password now fails with 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"cui\":\"%s\",\"password\":\"%s\"}", CUI, TEMPORARY_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Step 7: Subsequent activation attempt on now ACTIVE account fails with generic 401
        mockMvc.perform(post("/api/v1/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "cui": "%s",
                                  "temporaryPassword": "%s",
                                  "newPassword": "AnotherPassword789!"
                                }
                                """, CUI, TEMPORARY_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid activation credentials"));
    }
}
