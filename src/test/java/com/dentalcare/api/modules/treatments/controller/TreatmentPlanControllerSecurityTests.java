package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanItemResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.service.TreatmentPlanService;
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

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TreatmentPlanController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class TreatmentPlanControllerSecurityTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreatmentPlanService treatmentPlanService;
    @MockitoBean private JwtService jwtService;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/patients/{id}/treatment-plans", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{id}/treatment-plans", id)
                .contentType("application/json").content(validBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-plans/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/treatment-plans/{id}", id)
                .contentType("application/json").content(validBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/treatment-plans/{id}/approve", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/treatment-plans/professionals")).andExpect(status().isUnauthorized());
    }

    @Test
    void readPermissionIsIsolatedToReadEndpointsAndSafeCatalog() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        token("reader", "TREATMENT_PLAN_READ");
        TreatmentPlanResponse response = response(planId, patientId);
        when(treatmentPlanService.findByPatient(patientId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(treatmentPlanService.findById(planId)).thenReturn(response);
        when(treatmentPlanService.findProfessionals()).thenReturn(List.of(response.professional()));

        mockMvc.perform(get("/api/v1/patients/{id}/treatment-plans", patientId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(planId.toString()));
        mockMvc.perform(get("/api/v1/treatment-plans/{id}", planId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(20.00));
        mockMvc.perform(get("/api/v1/treatment-plans/professionals")
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].fullName").value("Dra. Segura"))
                .andExpect(content().string(not(containsString("username"))))
                .andExpect(content().string(not(containsString("email"))))
                .andExpect(content().string(not(containsString("status"))));

        mockMvc.perform(post("/api/v1/patients/{id}/treatment-plans", patientId)
                .header("Authorization", "Bearer reader").contentType("application/json").content(validBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/treatment-plans/{id}", planId)
                .header("Authorization", "Bearer reader").contentType("application/json").content(validBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/treatment-plans/{id}/approve", planId)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutationPermissionsAreIndependentAndReturnExpectedStatuses() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        TreatmentPlanResponse response = response(planId, patientId);

        token("creator", "TREATMENT_PLAN_CREATE");
        when(treatmentPlanService.create(eq(patientId), any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/patients/{id}/treatment-plans", patientId)
                .header("Authorization", "Bearer creator").contentType("application/json").content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/treatment-plans/" + planId)));
        mockMvc.perform(put("/api/v1/treatment-plans/{id}", planId)
                .header("Authorization", "Bearer creator").contentType("application/json").content(validBody()))
                .andExpect(status().isForbidden());

        token("updater", "TREATMENT_PLAN_UPDATE");
        when(treatmentPlanService.update(eq(planId), any())).thenReturn(response);
        mockMvc.perform(put("/api/v1/treatment-plans/{id}", planId)
                .header("Authorization", "Bearer updater").contentType("application/json").content(validBody()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/treatment-plans/{id}/approve", planId)
                .header("Authorization", "Bearer updater"))
                .andExpect(status().isForbidden());

        token("approver", "TREATMENT_PLAN_APPROVE");
        when(treatmentPlanService.approve(planId)).thenReturn(response);
        mockMvc.perform(patch("/api/v1/treatment-plans/{id}/approve", planId)
                .header("Authorization", "Bearer approver"))
                .andExpect(status().isOk());
    }

    @Test
    void validatesItemsBeforeCallingService() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("creator", "TREATMENT_PLAN_CREATE");
        for (String body : List.of(
                "{\"name\":\"Plan\",\"professionalId\":\"%s\",\"items\":[]}".formatted(UUID.randomUUID()),
                "{\"name\":\"Plan\",\"professionalId\":\"%s\",\"items\":[{\"name\":\"X\",\"quantity\":0,\"unitPrice\":10}]}".formatted(UUID.randomUUID()),
                "{\"name\":\"Plan\",\"professionalId\":\"%s\",\"items\":[{\"name\":\"X\",\"quantity\":1,\"unitPrice\":0}]}".formatted(UUID.randomUUID()))) {
            mockMvc.perform(post("/api/v1/patients/{id}/treatment-plans", patientId)
                    .header("Authorization", "Bearer creator").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(treatmentPlanService, never()).create(any(), any());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private String validBody() {
        return """
                {"name":"Plan","observations":null,"professionalId":"%s",
                 "items":[{"name":"Consulta","tooth":null,"quantity":1,"unitPrice":20.00}]}
                """.formatted(UUID.randomUUID());
    }

    private TreatmentPlanResponse response(UUID planId, UUID patientId) {
        var professional = new TreatmentPlanProfessionalResponse(UUID.randomUUID(), "Dra. Segura");
        var item = new TreatmentPlanItemResponse(UUID.randomUUID(), "Consulta", null, 1,
                new BigDecimal("20.00"), 0, new BigDecimal("20.00"));
        return new TreatmentPlanResponse(planId, patientId, "Plan", null, professional,
                TreatmentPlanStatus.DRAFT, List.of(item), new BigDecimal("20.00"),
                Instant.EPOCH, Instant.EPOCH, null);
    }
}
