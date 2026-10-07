package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.service.PatientTreatmentPlanService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientTreatmentPlanController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PatientTreatmentPlanControllerSecurityTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PatientTreatmentPlanService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void requiresAuthenticationAndPatientRole() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/treatment-plans"))
                .andExpect(status().isUnauthorized());

        token("staff", UUID.randomUUID(), "ROLE_DENTIST", "TREATMENT_PLAN_READ");
        mockMvc.perform(get("/api/v1/patients/me/treatment-plans")
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void derivesPatientFromJwtAndExposesOnlyPatientDto() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PatientTreatmentPlanResponse response = new PatientTreatmentPlanResponse(
                planId, "Plan integral", TreatmentPlanStatus.APPROVED, "Dra. Ana López",
                Instant.EPOCH, Instant.EPOCH, 2, 1, 50, List.of());
        token("patient", userId, "ROLE_PATIENT");
        when(service.findMine(userId, 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        when(service.findMineById(userId, planId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/patients/me/treatment-plans")
                        .param("patientId", UUID.randomUUID().toString())
                        .param("userId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.content[0].progressPercentage").value(50))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("patientId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("observations"))));
        mockMvc.perform(get("/api/v1/patients/me/treatment-plans/{id}", planId)
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());

        verify(service).findMine(userId, 0, 20);
        verify(service).findMineById(userId, planId);
    }

    @Test
    void foreignUnpublishedAndUnknownPlanIdsShareSafeNotFoundResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        token("patient", userId, "ROLE_PATIENT");
        for (UUID hiddenId : List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())) {
            when(service.findMineById(userId, hiddenId))
                    .thenThrow(new ResourceNotFoundException("Treatment plan not found"));
            mockMvc.perform(get("/api/v1/patients/me/treatment-plans/{id}", hiddenId)
                            .header("Authorization", "Bearer patient"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Treatment plan not found"));
        }
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }
}
