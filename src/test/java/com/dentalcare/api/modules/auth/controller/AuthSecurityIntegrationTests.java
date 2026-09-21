package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.service.AuthService;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class AuthSecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;

    @Test
    void validAccessTokenAuthenticatesCurrentUserRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("valid")).thenReturn(new JwtService.AccessTokenClaims(id, List.of("USER_READ")));
        when(authService.getCurrentUser(id)).thenReturn(
                new UserResponse(id, "user", "user@example.com", UserStatus.ACTIVE, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void missingTokenOnProtectedEndpointReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void invalidTokenOnProtectedEndpointReturns401() throws Exception {
        when(jwtService.parseAccessToken("invalid")).thenThrow(new IllegalArgumentException("signature details"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication is required"));
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
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"missing\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
}
