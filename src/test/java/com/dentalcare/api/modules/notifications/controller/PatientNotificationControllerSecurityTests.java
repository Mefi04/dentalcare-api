package com.dentalcare.api.modules.notifications.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.notifications.dto.response.NotificationPreferencesResponse;
import com.dentalcare.api.modules.notifications.dto.response.PatientNotificationResponse;
import com.dentalcare.api.modules.notifications.model.NotificationCategory;
import com.dentalcare.api.modules.notifications.model.NotificationEventType;
import com.dentalcare.api.modules.notifications.service.PatientNotificationService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PatientNotificationController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PatientNotificationControllerSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean PatientNotificationService service;
    @MockitoBean JwtService jwt;

    @Test
    void patientCanUseEveryOwnNotificationContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        token("patient", userId, "ROLE_PATIENT");
        var response = new PatientNotificationResponse(notificationId, NotificationEventType.PAYMENT_REGISTERED,
                NotificationCategory.PAYMENT, "Pago", "Pago registrado", false, null, Instant.now());
        when(service.findOwn(userId, 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        when(service.markAsRead(userId, notificationId)).thenReturn(response);
        when(service.markAllAsRead(userId)).thenReturn(2);
        when(service.findPreferences(userId)).thenReturn(new NotificationPreferencesResponse(true, true, true, true, Instant.now()));
        when(service.updatePreferences(eq(userId), any())).thenReturn(new NotificationPreferencesResponse(true, false, true, false, Instant.now()));

        mvc.perform(get("/api/v1/patients/me/notifications").header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(notificationId.toString()));
        mvc.perform(patch("/api/v1/patients/me/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/patients/me/notifications/read-all")
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(2));
        mvc.perform(get("/api/v1/patients/me/notifications/preferences")
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/patients/me/notifications/preferences")
                        .header("Authorization", "Bearer patient").contentType("application/json")
                        .content("{\"appointmentsEnabled\":true,\"paymentsEnabled\":false,\"medicationsEnabled\":true,\"clinicUpdatesEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentsEnabled").value(false));
    }

    @Test
    void requiresAuthenticationAndPatientRoleWithoutInvokingService() throws Exception {
        mvc.perform(get("/api/v1/patients/me/notifications")).andExpect(status().isUnauthorized());
        token("admin", UUID.randomUUID(), "ROLE_ADMINISTRATOR");
        mvc.perform(get("/api/v1/patients/me/notifications").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void validatesCompletePreferencePayload() throws Exception {
        token("patient", UUID.randomUUID(), "ROLE_PATIENT");
        mvc.perform(put("/api/v1/patients/me/notifications/preferences")
                        .header("Authorization", "Bearer patient").contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.appointmentsEnabled").exists())
                .andExpect(jsonPath("$.fieldErrors.paymentsEnabled").exists());
        verifyNoInteractions(service);
    }

    private void token(String token, UUID userId, String authority) {
        when(jwt.parseAccessToken(token)).thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authority)));
    }
}
