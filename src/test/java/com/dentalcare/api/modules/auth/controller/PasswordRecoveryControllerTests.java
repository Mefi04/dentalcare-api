package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.auth.dto.response.PasswordRecoveryResponse;
import com.dentalcare.api.modules.auth.service.PasswordRecoveryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordRecoveryController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PasswordRecoveryControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PasswordRecoveryService passwordRecoveryService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtProperties jwtProperties;
    @MockitoBean private com.dentalcare.api.security.ratelimit.RateLimitService rateLimitService;

    @Test
    void publicRequestReturnsAcceptedGenericResponse() throws Exception {
        when(passwordRecoveryService.requestRecovery(any()))
                .thenReturn(new PasswordRecoveryResponse(
                        "If the account is eligible, a recovery code will be sent"));

        mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"1234567890123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If the account is eligible, a recovery code will be sent"));
    }

    @Test
    void malformedRequestIsRejectedBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cui\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cui")
                        .value("CUI must contain exactly 13 digits"));

        verifyNoInteractions(passwordRecoveryService);
    }

    @Test
    void publicConfirmationReturnsSuccess() throws Exception {
        when(passwordRecoveryService.confirmRecovery(any()))
                .thenReturn(new PasswordRecoveryResponse("Password updated successfully"));

        mockMvc.perform(post("/api/v1/auth/password-recovery/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cui":"1234567890123","code":"12345678",
                                 "newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }

    @Test
    void invalidOrExpiredConfirmationReturnsGenericBadRequest() throws Exception {
        when(passwordRecoveryService.confirmRecovery(any()))
                .thenThrow(new BadRequestException("Invalid or expired recovery credentials"));

        mockMvc.perform(post("/api/v1/auth/password-recovery/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cui":"1234567890123","code":"12345678",
                                 "newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired recovery credentials"));
    }
}
