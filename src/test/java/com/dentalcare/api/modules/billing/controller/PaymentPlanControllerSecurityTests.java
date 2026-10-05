package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.billing.dto.request.CancelPaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.response.InstallmentResponse;
import com.dentalcare.api.modules.billing.dto.response.InstallmentStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanViewStatus;
import com.dentalcare.api.modules.billing.service.PaymentPlanService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentPlanController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PaymentPlanControllerSecurityTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-04T15:00:00Z");
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2099, 6, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentPlanService paymentPlanService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void endpointsRejectMissingToken() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{patientId}/payment-plans", patientId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payment-plans/{planId}/cancel", patientId, planId)
                        .contentType("application/json")
                        .content("{\"reason\":\"Cliente desistió\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsRejectCallerWithoutPlanManageAuthority() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        authenticate("reader-token", UUID.randomUUID(), "BILLING_READ");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer reader-token")
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/{patientId}/payment-plans/{planId}/cancel", patientId, planId)
                        .header("Authorization", "Bearer reader-token")
                        .contentType("application/json")
                        .content("{\"reason\":\"Cliente desistió\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readsRejectCallerWithoutBillingReadAuthority() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        authenticate("manager-token", UUID.randomUUID(), "BILLING_PLAN_MANAGE");

        mockMvc.perform(get("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/{patientId}/payment-plans", patientId)
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        authenticate("cashier-token", UUID.randomUUID(), "BILLING_PLAN_MANAGE");

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"installmentsCount\":1,\"firstDueDate\":\"2099-06-01\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"installmentsCount\":61,\"firstDueDate\":\"2099-06-01\"}"))
                .andExpect(status().isBadRequest());

        verify(paymentPlanService, never()).create(any(), any(), any(), any());
    }

    @Test
    void cancelRejectsBlankReason() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        authenticate("cashier-token", UUID.randomUUID(), "BILLING_PLAN_MANAGE");

        mockMvc.perform(post("/api/v1/patients/{patientId}/payment-plans/{planId}/cancel", patientId, planId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(paymentPlanService, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void createUsesJwtUserAndReturnsThePlan() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        PaymentPlanResponse plan = plan(patientId, chargeId, actorId);
        authenticate("cashier-token", actorId, "BILLING_PLAN_MANAGE");
        when(paymentPlanService.create(eq(patientId), eq(chargeId), eq(actorId), any())).thenReturn(plan);

        mockMvc.perform(post("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("""
                                {"patientId":"%s","totalAmount":999.99,"installmentsCount":3,"firstDueDate":"2099-06-01"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(plan.id().toString()))
                .andExpect(jsonPath("$.chargeId").value(chargeId.toString()))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.installmentsCount").value(3))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.pendingAmount").value(100.00))
                .andExpect(jsonPath("$.firstDueDate").value("2099-06-01"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-04T15:00:00Z"))
                .andExpect(jsonPath("$.createdByUserId").value(actorId.toString()))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].amount").value(33.33))
                .andExpect(jsonPath("$.installments[0].paidAmount").value(0.00))
                .andExpect(jsonPath("$.installments[0].status").value("PENDING"))
                .andExpect(jsonPath("$.installments[0].overdue").value(false))
                .andExpect(jsonPath("$.installments[2].amount").value(33.34));

        ArgumentCaptor<CreatePaymentPlanRequest> request = ArgumentCaptor.forClass(CreatePaymentPlanRequest.class);
        verify(paymentPlanService).create(eq(patientId), eq(chargeId), eq(actorId), request.capture());
        assertThat(request.getValue().installmentsCount()).isEqualTo(3);
        assertThat(request.getValue().firstDueDate()).isEqualTo(FIRST_DUE_DATE);
    }

    @Test
    void readsReturnThePlanShape() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PaymentPlanResponse plan = plan(patientId, chargeId, actorId);
        authenticate("reader-token", UUID.randomUUID(), "BILLING_READ");
        when(paymentPlanService.findActiveByCharge(patientId, chargeId)).thenReturn(plan);
        when(paymentPlanService.list(patientId)).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/v1/patients/{patientId}/charges/{chargeId}/payment-plan", patientId, chargeId)
                        .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.installments[0].number").value(1));
        mockMvc.perform(get("/api/v1/patients/{patientId}/payment-plans", patientId)
                        .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(plan.id().toString()))
                .andExpect(jsonPath("$[0].chargeId").value(chargeId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void cancelUsesJwtUser() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PaymentPlanResponse cancelled = new PaymentPlanResponse(
                planId, chargeId, patientId, PaymentPlanViewStatus.CANCELLED, 3,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), FIRST_DUE_DATE,
                CREATED_AT, actorId, CREATED_AT, actorId, "Cliente desistió", List.of());
        authenticate("cashier-token", actorId, "BILLING_PLAN_MANAGE");
        when(paymentPlanService.cancel(eq(patientId), eq(planId), eq(actorId), any())).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/patients/{patientId}/payment-plans/{planId}/cancel", patientId, planId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"reason\":\"Cliente desistió\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cliente desistió"))
                .andExpect(jsonPath("$.cancelledByUserId").value(actorId.toString()));

        ArgumentCaptor<CancelPaymentPlanRequest> request = ArgumentCaptor.forClass(CancelPaymentPlanRequest.class);
        verify(paymentPlanService).cancel(eq(patientId), eq(planId), eq(actorId), request.capture());
        assertThat(request.getValue().reason()).isEqualTo("Cliente desistió");
    }

    private void authenticate(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private String validCreateBody() {
        return "{\"installmentsCount\":3,\"firstDueDate\":\"2099-06-01\"}";
    }

    private PaymentPlanResponse plan(UUID patientId, UUID chargeId, UUID actorId) {
        return new PaymentPlanResponse(
                UUID.randomUUID(), chargeId, patientId, PaymentPlanViewStatus.ACTIVE, 3,
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("100.00"), FIRST_DUE_DATE,
                CREATED_AT, actorId, null, null, null,
                List.of(
                        new InstallmentResponse(1, new BigDecimal("33.33"), new BigDecimal("0.00"),
                                FIRST_DUE_DATE, InstallmentStatus.PENDING, false),
                        new InstallmentResponse(2, new BigDecimal("33.33"), new BigDecimal("0.00"),
                                FIRST_DUE_DATE.plusMonths(1), InstallmentStatus.PENDING, false),
                        new InstallmentResponse(3, new BigDecimal("33.34"), new BigDecimal("0.00"),
                                FIRST_DUE_DATE.plusMonths(2), InstallmentStatus.PENDING, false)));
    }
}
