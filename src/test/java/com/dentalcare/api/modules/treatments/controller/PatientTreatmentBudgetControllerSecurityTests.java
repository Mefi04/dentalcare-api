package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.model.PatientBudgetDecision;
import com.dentalcare.api.modules.treatments.service.PatientTreatmentBudgetService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientTreatmentBudgetController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PatientTreatmentBudgetControllerSecurityTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PatientTreatmentBudgetService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void requiresAuthenticationAndPatientRole() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/treatment-budgets")).andExpect(status().isUnauthorized());
        token("staff", UUID.randomUUID(), "ROLE_DENTIST", "TREATMENT_BUDGET_READ");
        mockMvc.perform(get("/api/v1/patients/me/treatment-budgets")
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void derivesOwnerFromJwtAndNeverExposesInternalActorsOrPatientId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        var response = response(budgetId, PatientBudgetDecision.PENDING);
        token("patient", userId, "ROLE_PATIENT");
        when(service.findMine(userId, 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        when(service.findMineById(userId, budgetId)).thenReturn(response);
        when(service.accept(userId, budgetId)).thenReturn(response(budgetId, PatientBudgetDecision.ACCEPTED));
        when(service.reject(userId, budgetId)).thenReturn(response(budgetId, PatientBudgetDecision.REJECTED));

        mockMvc.perform(get("/api/v1/patients/me/treatment-budgets")
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(budgetId.toString()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("patientId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("generatedBy"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("decidedBy"))));
        mockMvc.perform(get("/api/v1/patients/me/treatment-budgets/{id}", budgetId)
                        .header("Authorization", "Bearer patient")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/patients/me/treatment-budgets/{id}/accept", budgetId)
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("ACCEPTED"));
        mockMvc.perform(patch("/api/v1/patients/me/treatment-budgets/{id}/reject", budgetId)
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("REJECTED"));

        verify(service).findMine(userId, 0, 20);
        verify(service).findMineById(userId, budgetId);
        verify(service).accept(userId, budgetId);
        verify(service).reject(userId, budgetId);
    }

    private PatientTreatmentBudgetResponse response(UUID id, PatientBudgetDecision decision) {
        return new PatientTreatmentBudgetResponse(id, UUID.randomUUID(), "Plan integral", 1,
                new BigDecimal("700.00"), new BigDecimal("700.00"), decision, List.of(),
                Instant.EPOCH, Instant.EPOCH, decision == PatientBudgetDecision.PENDING ? null : Instant.EPOCH);
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }
}
