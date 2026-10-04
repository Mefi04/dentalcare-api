package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;
import com.dentalcare.api.modules.billing.dto.response.RefundResponse;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.service.ChargeAdjustmentService;
import com.dentalcare.api.modules.billing.service.RefundResult;
import com.dentalcare.api.modules.billing.service.RefundService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChargeAdjustmentController.class, RefundController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class BillingAdjustmentControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private ChargeAdjustmentService chargeAdjustmentService;

    @MockitoBean
    private RefundService refundService;

    @Test
    void endpointsRejectMissingToken() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/void", patientId, chargeId)
                        .contentType(MediaType.APPLICATION_JSON).content(reasonJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{patientId}/charge-adjustments", patientId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{patientId}/refunds", patientId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsRejectCallerWithoutTheSpecificAuthority() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("reader", UUID.randomUUID(), "BILLING_READ");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .header("Authorization", "Bearer reader")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/void", patientId, chargeId)
                        .header("Authorization", "Bearer reader")
                        .contentType(MediaType.APPLICATION_JSON).content(reasonJson()))
                .andExpect(status().isForbidden());
        authenticate("adjuster", UUID.randomUUID(), "BILLING_ADJUSTMENT_CREATE");
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer adjuster")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsRejectCallerWithoutBillingRead() throws Exception {
        UUID patientId = UUID.randomUUID();
        authenticate("adjuster", UUID.randomUUID(), "BILLING_ADJUSTMENT_CREATE");
        mockMvc.perform(get("/api/v1/patients/{patientId}/charge-adjustments", patientId)
                        .header("Authorization", "Bearer adjuster"))
                .andExpect(status().isForbidden());

        authenticate("refunder", UUID.randomUUID(), "BILLING_REFUND_CREATE");
        mockMvc.perform(get("/api/v1/patients/{patientId}/refunds", patientId)
                        .header("Authorization", "Bearer refunder"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierIsForbiddenOnDiscountVoidAndRefund() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("cashier", UUID.randomUUID(), "ROLE_CASHIER", "BILLING_READ", "BILLING_CHARGE_CREATE",
                "BILLING_PAYMENT_CREATE", "BILLING_CASH_MANAGE");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .header("Authorization", "Bearer cashier")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/void", patientId, chargeId)
                        .header("Authorization", "Bearer cashier")
                        .contentType(MediaType.APPLICATION_JSON).content(reasonJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer cashier")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadIsBadRequest() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("admin", UUID.randomUUID(), "BILLING_ADJUSTMENT_CREATE", "BILLING_REFUND_CREATE");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"0.00\",\"reason\":\"Cortesia\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.001\",\"reason\":\"Cortesia\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/void", patientId, chargeId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"-1.00\",\"reason\":\"Devolucion\"}"))
                .andExpect(status().isBadRequest());

        verify(chargeAdjustmentService, never()).discount(any(), any(), any(), any());
        verify(chargeAdjustmentService, never()).voidCharge(any(), any(), any(), any());
        verify(refundService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void blankOrTooLongIdempotencyKeyIsBadRequest() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("admin", UUID.randomUUID(), "BILLING_REFUND_CREATE");
        String tooLong = "k".repeat(101);

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .header("Idempotency-Key", tooLong)
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isBadRequest());

        verify(refundService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void discountAndVoidUseTheJwtUser() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        authenticate("admin", actorId, "BILLING_ADJUSTMENT_CREATE");
        when(chargeAdjustmentService.discount(eq(patientId), eq(chargeId), eq(actorId), any()))
                .thenReturn(adjustment(patientId, chargeId, actorId));
        when(chargeAdjustmentService.voidCharge(eq(patientId), eq(chargeId), eq(actorId), any()))
                .thenReturn(adjustment(patientId, chargeId, actorId));

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/discounts", patientId, chargeId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedByUserId").value(actorId.toString()));
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/void", patientId, chargeId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content(reasonJson()))
                .andExpect(status().isCreated());

        verify(chargeAdjustmentService).discount(eq(patientId), eq(chargeId), eq(actorId), any());
        verify(chargeAdjustmentService).voidCharge(eq(patientId), eq(chargeId), eq(actorId), any());
    }

    @Test
    void firstRefundIsCreatedAndTheIdempotentReplayIsOk() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("admin", actorId, "BILLING_REFUND_CREATE");
        RefundResponse refund = refund(patientId, paymentId, actorId);
        when(refundService.create(eq(patientId), eq(paymentId), eq(actorId), any(), eq("key-1")))
                .thenReturn(new RefundResult(refund, false), new RefundResult(refund, true));

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedByUserId").value(actorId.toString()));
        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refund.id().toString()));
    }

    @Test
    void listsUseBillingRead() throws Exception {
        UUID patientId = UUID.randomUUID();
        authenticate("reader", UUID.randomUUID(), "BILLING_READ");
        when(chargeAdjustmentService.list(patientId)).thenReturn(List.of());
        when(refundService.list(patientId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/patients/{patientId}/charge-adjustments", patientId)
                        .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/v1/patients/{patientId}/refunds", patientId)
                        .header("Authorization", "Bearer reader"))
                .andExpect(status().isOk());
    }

    @Test
    void refundWithoutIdempotencyKeyStillReachesTheService() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("admin", actorId, "BILLING_REFUND_CREATE");
        when(refundService.create(eq(patientId), eq(paymentId), eq(actorId), any(), isNull()))
                .thenReturn(new RefundResult(refund(patientId, paymentId, actorId), false));

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/refunds", patientId, paymentId)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content(discountJson()))
                .andExpect(status().isCreated());

        verify(refundService).create(eq(patientId), eq(paymentId), eq(actorId), any(), isNull());
    }

    private void authenticate(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private ChargeAdjustmentResponse adjustment(UUID patientId, UUID chargeId, UUID actorId) {
        return new ChargeAdjustmentResponse(UUID.randomUUID(), chargeId, patientId, ChargeAdjustmentType.DISCOUNT,
                new BigDecimal("10.00"), "Cortesia", actorId, actorId, NOW);
    }

    private RefundResponse refund(UUID patientId, UUID paymentId, UUID actorId) {
        return new RefundResponse(UUID.randomUUID(), paymentId, patientId, new BigDecimal("10.00"), "Devolucion",
                actorId, actorId, null, NOW);
    }

    private String discountJson() {
        return "{\"amount\":\"10.00\",\"reason\":\"Cortesia\"}";
    }

    private String reasonJson() {
        return "{\"reason\":\"Error de captura\"}";
    }
}
