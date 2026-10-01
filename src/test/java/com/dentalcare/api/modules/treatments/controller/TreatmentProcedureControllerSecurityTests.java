package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentProcedureResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.treatments.service.TreatmentProcedureService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TreatmentProcedureController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class TreatmentProcedureControllerSecurityTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreatmentProcedureService service;
    @MockitoBean private JwtService jwtService;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/procedures", id)
                .contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-procedures/{id}/complete", id)
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-procedures/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/procedures", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{id}/treatment-procedures", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void permissionsSeparateReadExecuteAndComplete() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        TreatmentProcedureResponse response = response(planId, itemId);

        token("reader", userId, "TREATMENT_PROCEDURE_READ");
        when(service.findById(response.id())).thenReturn(response);
        when(service.findByPlan(planId, 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        when(service.findByPatient(response.patientId(), 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        mockMvc.perform(get("/api/v1/treatment-procedures/{id}", response.id())
                .header("Authorization", "Bearer reader")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/treatment-plans/{id}/procedures", planId)
                .header("Authorization", "Bearer reader")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/patients/{id}/treatment-procedures", response.patientId())
                .header("Authorization", "Bearer reader")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/procedures", planId)
                .header("Authorization", "Bearer reader").contentType("application/json").content(body(itemId)))
                .andExpect(status().isForbidden());

        token("executor", userId, "TREATMENT_PROCEDURE_EXECUTE");
        when(service.register(eq(planId), eq(userId), any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/procedures", planId)
                .header("Authorization", "Bearer executor").contentType("application/json").content(body(itemId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/treatment-procedures/")))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        verify(service).register(eq(planId), eq(userId), any());

        token("completer", userId, "TREATMENT_PROCEDURE_COMPLETE");
        when(service.complete(eq(response.id()), eq(userId), any())).thenReturn(response);
        mockMvc.perform(patch("/api/v1/treatment-procedures/{id}/complete", response.id())
                .header("Authorization", "Bearer completer")
                .contentType("application/json").content("{\"completionNotes\":\"Correcto\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void validatesRequiredPlanItemBeforeService() throws Exception {
        token("executor", UUID.randomUUID(), "TREATMENT_PROCEDURE_EXECUTE");
        mockMvc.perform(post("/api/v1/treatment-plans/{id}/procedures", UUID.randomUUID())
                .header("Authorization", "Bearer executor")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void token(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private String body() { return body(UUID.randomUUID()); }

    private String body(UUID itemId) {
        return "{\"treatmentPlanItemId\":\"%s\",\"clinicalObservations\":\"Preparación correcta\"}"
                .formatted(itemId);
    }

    private TreatmentProcedureResponse response(UUID planId, UUID itemId) {
        return new TreatmentProcedureResponse(UUID.randomUUID(), planId, itemId, UUID.randomUUID(),
                new TreatmentPlanProfessionalResponse(UUID.randomUUID(), "Dra. Prueba"),
                "Restauración", "16", 1, null, null,
                TreatmentProcedureStatus.IN_PROGRESS, Instant.EPOCH, null);
    }
}
