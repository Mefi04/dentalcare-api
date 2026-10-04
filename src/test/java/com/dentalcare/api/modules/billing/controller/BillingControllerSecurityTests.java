package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.AccountSummaryResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.service.BillingService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertInvalid("charges", patientId, "{\"concept\":\"  \",\"amount\":10.00}", "concept");
        assertInvalid("charges", patientId, "{\"concept\":\"Consulta\"}", "amount");
        assertInvalid("charges", patientId, "{\"concept\":\"Consulta\",\"amount\":0}", "amount");
        assertInvalid("charges", patientId, "{\"concept\":\"Consulta\",\"amount\":-5}", "amount");
        assertInvalid("charges", patientId, "{\"concept\":\"Consulta\",\"amount\":10.005}", "amount");
        assertInvalid("charges", patientId,
                "{\"concept\":\"%s\",\"amount\":10}".formatted("x".repeat(201)), "concept");

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

    @Test
    void registerPaymentRequiresPaymentCreatePermissionAndUsesPathPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        String body = """
                {"patientId":"%s","chargeId":"%s","amount":50.00,"method":"CASH","kind":"ADVANCE","balance":0}
                """.formatted(UUID.randomUUID(), chargeId);

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        for (String authority : List.of("BILLING_READ", "BILLING_CHARGE_CREATE", "PATIENT_UPDATE", "ROLE_PATIENT")) {
            token("token-" + authority, authority);
            mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                            .header("Authorization", "Bearer token-" + authority)
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }

        UUID cashierId = token("cashier-token", "ROLE_CASHIER", "BILLING_PAYMENT_CREATE");
        when(billingService.registerPayment(eq(patientId), any(), eq(cashierId))).thenReturn(new PaymentResponse(
                UUID.randomUUID(), chargeId, PaymentKind.PARTIAL_PAYMENT, PaymentMethod.CASH,
                new BigDecimal("50.00"), NOW));
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chargeId").value(chargeId.toString()))
                .andExpect(jsonPath("$.kind").value("PARTIAL_PAYMENT"))
                .andExpect(jsonPath("$.method").value("CASH"));

        ArgumentCaptor<CreatePaymentRequest> request = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(billingService).registerPayment(eq(patientId), request.capture(), eq(cashierId));
        assertThat(request.getValue().chargeId()).isEqualTo(chargeId);
        assertThat(request.getValue().amount()).isEqualByComparingTo("50.00");
        assertThat(request.getValue().method()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void registerPaymentRejectsInvalidPayloadsBeforeCallingService() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("cashier-token", "BILLING_PAYMENT_CREATE");

        assertInvalid("payments", patientId, "{\"amount\":10.00}", "method");
        assertInvalid("payments", patientId, "{\"method\":\"CASH\"}", "amount");
        assertInvalid("payments", patientId, "{\"amount\":0,\"method\":\"CASH\"}", "amount");
        assertInvalid("payments", patientId, "{\"amount\":1.234,\"method\":\"CASH\"}", "amount");

        for (String body : List.of(
                "{\"amount\":10,\"method\":\"BITCOIN\"}",
                "{\"chargeId\":\"not-a-uuid\",\"amount\":10,\"method\":\"CASH\"}")) {
            mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                            .header("Authorization", "Bearer cashier-token")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request is malformed or contains an invalid value"));
        }

        verify(billingService, never()).registerPayment(any(), any(), any());
    }

    @Test
    void paymentBusinessErrorsUseStandardErrorContract() throws Exception {
        UUID patientId = UUID.randomUUID();
        token("cashier-token", "BILLING_PAYMENT_CREATE");
        String body = "{\"chargeId\":\"%s\",\"amount\":10,\"method\":\"CASH\"}".formatted(UUID.randomUUID());

        when(billingService.registerPayment(eq(patientId), any(), any()))
                .thenThrow(new ConflictException("Payment amount exceeds the pending balance of the charge"))
                .thenThrow(new ResourceNotFoundException("Charge not found"));

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Payment amount exceeds the pending balance of the charge"));

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments", patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Charge not found"));
    }

    private void assertInvalid(String resource, UUID patientId, String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/patients/{patientId}/" + resource, patientId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    private UUID token(String token, String... authorities) {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
        return userId;
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
