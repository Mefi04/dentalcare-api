package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.CorsConfig;
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
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MobileAuthController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, AuthCookieManager.class})
class MobileAuthControllerSecurityTests {

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
    void successfulMobileLoginReturnsTokensInBodyAndSetsNoCookies() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse userResponse = new UserResponse(id, "testuser", "Test User", "test@example.com", UserStatus.ACTIVE, List.of(), List.of());
        LoginResponse loginResponse = new LoginResponse("mobile-access-token-123", "Bearer", 1800L, userResponse);
        AuthService.LoginResult loginResult = new AuthService.LoginResult(loginResponse, "mobile-raw-refresh-token-456", Duration.ofDays(7));

        when(authService.login(new LoginRequest("1234567890123", "password123"))).thenReturn(loginResult);

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"1234567890123\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mobile-access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("mobile-raw-refresh-token-456"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.user.fullName").value("Test User"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).login(new LoginRequest("1234567890123", "password123"));
    }

    @Test
    void mobileLoginRejectsInvalidCuiWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"12345\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.cui").value("CUI must contain exactly 13 digits"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    void mobileLoginRejectsBlankPasswordWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"1234567890123\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    void mobileLoginWithInvalidCredentialsReturnsGeneric401() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"1234567890123\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void successfulMobileRefreshReturnsRotatedTokensAndSetsNoCookies() throws Exception {
        RefreshResponse refreshResponse = new RefreshResponse("new-mobile-jwt-789", "Bearer", 1800L);
        AuthService.RefreshResult refreshResult = new AuthService.RefreshResult(
                refreshResponse, "new-mobile-rotated-token-999", Duration.ofDays(7));

        when(authService.refresh("old-mobile-token-111")).thenReturn(refreshResult);

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-mobile-token-111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-mobile-jwt-789"))
                .andExpect(jsonPath("$.refreshToken").value("new-mobile-rotated-token-999"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).refresh("old-mobile-token-111");
    }

    @Test
    void mobileRefreshRejectsBlankTokenWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("Refresh token is required"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    void mobileRefreshRejectsMissingBodyWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    void mobileRefreshWithInvalidOrExpiredTokenReturnsGeneric401() throws Exception {
        when(authService.refresh("expired-or-invalid-token"))
                .thenThrow(new UnauthorizedException("Authentication is required"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-or-invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void mobileRefreshWithReusedTokenReturnsGeneric401() throws Exception {
        when(authService.refresh("reused-stolen-token"))
                .thenThrow(new UnauthorizedException("Authentication is required"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused-stolen-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void successfulMobileLogoutWithTokenReturns204AndSetsNoCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"mobile-token-to-revoke\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).logout("mobile-token-to-revoke");
    }

    @Test
    void mobileLogoutWithoutBodyIsIdempotentAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).logout(null);
    }

    @Test
    void mobileLogoutWithEmptyJsonIsIdempotentAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).logout(null);
    }

    @Test
    void mobileRefreshRejectsTokenExceedingMaxLengthWith400() throws Exception {
        String longToken = "a".repeat(256);
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", longToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("Refresh token must not exceed 255 characters"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    void mobileLogoutRejectsTokenExceedingMaxLengthWith400() throws Exception {
        String longToken = "a".repeat(256);
        mockMvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", longToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.refreshToken").value("Refresh token must not exceed 255 characters"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }
}
