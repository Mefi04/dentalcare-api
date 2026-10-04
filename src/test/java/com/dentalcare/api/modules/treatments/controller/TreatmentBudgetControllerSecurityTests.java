package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import com.dentalcare.api.modules.treatments.service.TreatmentBudgetService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TreatmentBudgetController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class TreatmentBudgetControllerSecurityTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreatmentBudgetService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/budgets", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/budgets", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-budgets/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-budgets/{id}/approve", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-budgets/{id}/reject", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void permissionsSeparateReadCreateAndDecision() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        TreatmentBudgetResponse response = response(planId);

        token("reader", userId, "TREATMENT_BUDGET_READ");
        when(service.findByPlan(planId)).thenReturn(List.of(response));
        when(service.findById(response.id())).thenReturn(response);
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/budgets", planId)
                .header("Authorization", "Bearer reader")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/budgets", planId)
                .header("Authorization", "Bearer reader")).andExpect(status().isForbidden());

        token("creator", userId, "TREATMENT_BUDGET_CREATE");
        when(service.generate(planId, userId)).thenReturn(response);
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/budgets", planId)
                .header("Authorization", "Bearer creator"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/treatment-budgets/")));
        verify(service).generate(planId, userId);

        token("decider", userId, "TREATMENT_BUDGET_DECIDE");
        when(service.approve(response.id(), userId)).thenReturn(response);
        mockMvc.perform(patch("/api/v1/treatment-budgets/{id}/approve", response.id())
                .header("Authorization", "Bearer decider")).andExpect(status().isOk());
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private TreatmentBudgetResponse response(UUID planId) {
        return new TreatmentBudgetResponse(UUID.randomUUID(), planId, UUID.randomUUID(), 1,
                Instant.EPOCH, BigDecimal.TEN, BigDecimal.TEN, TreatmentBudgetStatus.PENDING,
                null, null, List.of(), Instant.EPOCH, Instant.EPOCH, null);
    }
}
