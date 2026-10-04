package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentConsentResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentConsentStatus;
import com.dentalcare.api.modules.treatments.service.TreatmentConsentService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TreatmentConsentController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class TreatmentConsentControllerSecurityTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreatmentConsentService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/consents", id)
                .contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/consents", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-consents/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-consents/{id}/accept", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-consents/{id}/revoke", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void permissionsSeparateReadCreateAcceptAndRevoke() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        TreatmentConsentResponse response = response(planId);

        token("reader", userId, "TREATMENT_CONSENT_READ");
        when(service.findByPlan(planId)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/consents", planId)
                .header("Authorization", "Bearer reader")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/consents", planId)
                .header("Authorization", "Bearer reader")
                .contentType("application/json").content(body())).andExpect(status().isForbidden());

        token("creator", userId, "TREATMENT_CONSENT_CREATE");
        when(service.prepare(eq(planId), eq(userId), any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/consents", planId)
                .header("Authorization", "Bearer creator")
                .contentType("application/json").content(body()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/treatment-consents/")));
        verify(service).prepare(eq(planId), eq(userId), any());

        token("acceptor", userId, "TREATMENT_CONSENT_ACCEPT");
        when(service.accept(response.id(), userId)).thenReturn(response);
        mockMvc.perform(patch("/api/v1/treatment-consents/{id}/accept", response.id())
                .header("Authorization", "Bearer acceptor")).andExpect(status().isOk());

        token("revoker", userId, "TREATMENT_CONSENT_REVOKE");
        when(service.revoke(response.id(), userId)).thenReturn(response);
        mockMvc.perform(patch("/api/v1/treatment-consents/{id}/revoke", response.id())
                .header("Authorization", "Bearer revoker")).andExpect(status().isOk());
    }

    @Test
    void validatesConsentTextAndVersionBeforeService() throws Exception {
        token("creator", UUID.randomUUID(), "TREATMENT_CONSENT_CREATE");
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/consents", UUID.randomUUID())
                .header("Authorization", "Bearer creator")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private String body() {
        return "{\"documentVersion\":\"v1\",\"consentText\":\"Acepto el tratamiento\"}";
    }

    private TreatmentConsentResponse response(UUID planId) {
        return new TreatmentConsentResponse(UUID.randomUUID(), planId, UUID.randomUUID(), null,
                "v1", "Acepto el tratamiento", TreatmentConsentStatus.PENDING,
                null, null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }
}
