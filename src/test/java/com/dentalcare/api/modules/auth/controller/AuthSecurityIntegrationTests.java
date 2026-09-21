package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.service.AuthService;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.security.cookie.AuthCookieManager;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, AuthCookieManager.class})
class AuthSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        when(jwtProperties.cookieSecure()).thenReturn(false);
    }

    @Test
    void validAccessTokenAuthenticatesCurrentUserRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("valid")).thenReturn(new JwtService.AccessTokenClaims(id, List.of("USER_READ")));
        when(authService.getCurrentUser(id)).thenReturn(
                new UserResponse(id, "user", "user@example.com", UserStatus.ACTIVE, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void missingTokenOnProtectedEndpointReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void invalidTokenOnProtectedEndpointReturns401() throws Exception {
        when(jwtService.parseAccessToken("invalid")).thenThrow(new IllegalArgumentException("signature details"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void expiredTokenOnProtectedEndpointReturns401() throws Exception {
        when(jwtService.parseAccessToken("expired")).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginCredentialsReturnGeneric401() throws Exception {
        when(authService.login(new LoginRequest("missing", "wrong")))
                .thenThrow(new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"missing\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void successfulLoginSetsHttpOnlyCookieAndDoesNotIncludeRefreshTokenInBody() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse userResponse = new UserResponse(id, "testuser", "test@example.com", UserStatus.ACTIVE, List.of(), List.of());
        LoginResponse loginResponse = new LoginResponse("access-token-123", "Bearer", 1800L, userResponse);
        AuthService.LoginResult loginResult = new AuthService.LoginResult(loginResponse, "raw-refresh-cookie-value", Duration.ofDays(7));

        when(authService.login(new LoginRequest("testuser", "password123"))).thenReturn(loginResult);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=raw-refresh-cookie-value")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void refreshWithoutBearerTokenWorksWithCookieAndRotatesCookie() throws Exception {
        RefreshResponse refreshResponse = new RefreshResponse("new-access-token-456", "Bearer", 1800L);
        AuthService.RefreshResult refreshResult = new AuthService.RefreshResult(refreshResponse, "new-rotated-refresh-token", Duration.ofDays(6));

        when(authService.refresh("initial-refresh-token")).thenReturn(refreshResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "initial-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token-456"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-rotated-refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    void refreshWithoutCookieReturnsGeneric401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void refreshWithInvalidOrExpiredTokenReturnsGeneric401() throws Exception {
        when(authService.refresh("bad-token")).thenThrow(new UnauthorizedException("Authentication is required"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "bad-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void logoutWithoutBearerTokenClearsCookieAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "token-to-logout")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout("token-to-logout");
    }

    @Test
    void logoutWithoutCookieIsIdempotentAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout(null);
    }
}
