package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.AccountSummaryResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.service.BillingService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BillingController.class, PatientBillingController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class BillingControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void statementRequiresAuthenticationAndBillingReadPermission() throws Exception {
        UUID patientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/patients/{patientId}/account-statement", patientId))
                .andExpect(status().isUnauthorized());

        token("dentist-token", "ROLE_DENTIST", "PATIENT_READ", "MEDICAL_HISTORY_READ");
        mockMvc.perform(get("/api/v1/patients/{patientId}/account-statement", patientId)
                        .header("Authorization", "Bearer dentist-token"))
                .andExpect(status().isForbidden());

        token("patient-token", "ROLE_PATIENT");
        mockMvc.perform(get("/api/v1/patients/{patientId}/account-statement", patientId)
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden());

        token("reader-token", "ROLE_SECRETARY", "BILLING_READ");
        when(billingService.findAccountStatement(patientId)).thenReturn(statement(patientId));
        mockMvc.perform(get("/api/v1/patients/{patientId}/account-statement", patientId)
                        .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.charges[0].concept").value("Consulta"))
                .andExpect(jsonPath("$.charges[0].status").value("PENDING"))
                .andExpect(jsonPath("$.charges[0].patient").doesNotExist())
                .andExpect(content().string(containsString("\"balance\":150.00")));

        verify(billingService).findAccountStatement(patientId);
    }

    @Test
    void createChargeRequiresChargeCreatePermissionAndUsesPathPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID bodyPatientId = UUID.randomUUID();
        String body = """
                {"patientId":"%s","concept":"Consulta","amount":150.00,"paid":150.00,"balance":0}
                """.formatted(bodyPatientId);

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        for (String authority : List.of("BILLING_READ", "BILLING_PAYMENT_CREATE", "PATIENT_UPDATE")) {
            token("token-" + authority, authority);
            mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                            .header("Authorization", "Bearer token-" + authority)
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }

        token("cashier-token", "ROLE_CASHIER", "BILLING_CHARGE_CREATE");
        when(billingService.createCharge(eq(patientId), any())).thenReturn(charge());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.concept").value("Consulta"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(billingService).createCharge(eq(patientId), any());
        verify(billingService, never()).createCharge(eq(bodyPatientId), any());
    }

    @Test
    void createChargeRejectsInvalidPayloadsBeforeCallingService() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("cashier-token", "BILLING_CHARGE_CREATE");

        assertInvalidCharge(patientId, "{\"concept\":\"  \",\"amount\":10.00}", "concept");
        assertInvalidCharge(patientId, "{\"concept\":\"Consulta\"}", "amount");
        assertInvalidCharge(patientId, "{\"concept\":\"Consulta\",\"amount\":0}", "amount");
        assertInvalidCharge(patientId, "{\"concept\":\"Consulta\",\"amount\":-5}", "amount");
        assertInvalidCharge(patientId, "{\"concept\":\"Consulta\",\"amount\":10.005}", "amount");
        assertInvalidCharge(patientId, "{\"concept\":\"%s\",\"amount\":10}".formatted("x".repeat(201)), "concept");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content("{\"concept\":\"Consulta\",\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request is malformed or contains an invalid value"));

        verify(billingService, never()).createCharge(any(), any());
    }

    @Test
    void createChargeForUnknownPatientReturnsNotFound() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("cashier-token", "BILLING_CHARGE_CREATE");
        when(billingService.createCharge(eq(patientId), any()))
                .thenThrow(new ResourceNotFoundException("Patient not found"));

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content("{\"concept\":\"Consulta\",\"amount\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Patient not found"));
    }

    private void assertInvalidCharge(UUID patientId, String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private ChargeResponse charge() {
        return new ChargeResponse(UUID.randomUUID(), "Consulta", new BigDecimal("150.00"),
                new BigDecimal("0.00"), new BigDecimal("150.00"), ChargeStatus.PENDING, NOW);
    }

    private AccountStatementResponse statement(UUID patientId) {
        BigDecimal zero = new BigDecimal("0.00");
        BigDecimal amount = new BigDecimal("150.00");
        return new AccountStatementResponse(patientId,
                new AccountSummaryResponse(amount, zero, zero, amount),
                List.of(charge()), List.of());
    }
}
