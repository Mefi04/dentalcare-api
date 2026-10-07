package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.ChangePasswordRequest;
import com.dentalcare.api.modules.auth.service.AuthService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtProperties;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MobileAuthController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MobilePasswordChangeControllerTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean JwtProperties jwtProperties;
    @MockitoBean com.dentalcare.api.security.ratelimit.RateLimitService rateLimitService;

    @Test
    void authenticatedPatientCanChangeOnlyOwnPassword() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken("valid")).thenReturn(
                new JwtService.AccessTokenClaims(userId, List.of("ROLE_PATIENT")));

        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(userId,
                new ChangePasswordRequest("CurrentPassword123!", "NewPassword456!"));
    }

    @Test
    void authenticatedAdministratorCannotChangePasswordThroughPatientEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken("administrator")).thenReturn(
                new JwtService.AccessTokenClaims(userId, List.of("ROLE_ADMINISTRATOR")));

        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .header("Authorization", "Bearer administrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void invalidNewPasswordReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken("valid")).thenReturn(
                new JwtService.AccessTokenClaims(userId, List.of("ROLE_PATIENT")));
        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPassword123!\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword")
                        .value("New password must be between 8 and 128 characters"));
        verifyNoInteractions(authService);
    }

    @Test
    void incorrectCurrentPasswordUsesSafe401Response() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken("valid")).thenReturn(
                new JwtService.AccessTokenClaims(userId, List.of("ROLE_PATIENT")));
        doThrow(new UnauthorizedException("Invalid credentials")).when(authService)
                .changePassword(eq(userId), any());
        mockMvc.perform(patch("/api/v1/auth/mobile/password")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
