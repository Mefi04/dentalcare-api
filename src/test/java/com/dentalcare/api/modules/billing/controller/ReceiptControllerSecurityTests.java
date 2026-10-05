package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.dto.response.ReceiptClinicResponse;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;
import com.dentalcare.api.modules.billing.service.ReceiptService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReceiptController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ReceiptControllerSecurityTests {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-04T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void endpointsRejectMissingToken() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issueRejectsCallerWithoutReceiptCreateAuthority() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("reader-token", UUID.randomUUID(), "BILLING_READ");

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId)
                        .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findRejectsCallerWithoutBillingReadAuthority() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        authenticate("issuer-token", UUID.randomUUID(), "BILLING_RECEIPT_CREATE");

        mockMvc.perform(get("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId)
                        .header("Authorization", "Bearer issuer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void issueUsesJwtUserAndReturnsCreatedReceipt() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ReceiptResponse receipt = receipt(patientId, paymentId, actorId);
        authenticate("cashier-token", actorId, "BILLING_RECEIPT_CREATE");
        when(receiptService.issue(patientId, paymentId, actorId)).thenReturn(receipt);

        mockMvc.perform(post("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId)
                        .header("Authorization", "Bearer cashier-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(receipt.id().toString()))
                .andExpect(jsonPath("$.receiptNumber").value(12))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.concept").value("Limpieza"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").value("2026-10-04T15:00:00Z"))
                .andExpect(jsonPath("$.issuedByUserId").value(actorId.toString()))
                .andExpect(jsonPath("$.voidedAt").doesNotExist())
                .andExpect(jsonPath("$.voidReason").doesNotExist())
                .andExpect(jsonPath("$.clinic.tradeName").value("Clínica"));

        verify(receiptService).issue(patientId, paymentId, actorId);
    }

    @Test
    void findReturnsTheReceipt() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ReceiptResponse receipt = receipt(patientId, paymentId, actorId);
        authenticate("reader-token", UUID.randomUUID(), "BILLING_READ");
        when(receiptService.findByPayment(patientId, paymentId)).thenReturn(receipt);

        mockMvc.perform(get("/api/v1/patients/{patientId}/payments/{paymentId}/receipt", patientId, paymentId)
                        .header("Authorization", "Bearer reader-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receipt.id().toString()))
                .andExpect(jsonPath("$.receiptNumber").value(12))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.concept").value("Limpieza"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").value("2026-10-04T15:00:00Z"))
                .andExpect(jsonPath("$.issuedByUserId").value(actorId.toString()));
    }

    private void authenticate(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private ReceiptResponse receipt(UUID patientId, UUID paymentId, UUID actorId) {
        return new ReceiptResponse(UUID.randomUUID(), 12L, paymentId, patientId, "Limpieza",
                new BigDecimal("40.00"), PaymentMethod.CARD, ReceiptStatus.ISSUED, ISSUED_AT, actorId,
                null, null, new ReceiptClinicResponse("Clínica", null, null, null, null, null));
    }
}
