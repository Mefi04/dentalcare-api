package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.model.CashMovementType;
import com.dentalcare.api.modules.billing.model.CashShiftStatus;
import com.dentalcare.api.modules.billing.service.CashShiftService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(controllers = CashShiftController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class CashShiftControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashShiftService cashShiftService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void endpointsRejectMissingToken() throws Exception {
        UUID shiftId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/billing/cash-shifts/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/billing/cash-shifts").contentType("application/json")
                        .content("{\"openingAmount\":10.00}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/close", shiftId).contentType("application/json")
                        .content("{\"countedAmount\":10.00}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/billing/cash-shifts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId).contentType("application/json")
                        .content("{\"type\":\"INCOME\",\"amount\":10.00,\"concept\":\"Agua\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsRejectCallerWithoutCashManageAuthority() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        authenticate("reader-token", userId, "BILLING_READ", "BILLING_CASH_READ_ALL");

        mockMvc.perform(get("/api/v1/billing/cash-shifts/current").header("Authorization", "Bearer reader-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/billing/cash-shifts").header("Authorization", "Bearer reader-token")
                        .contentType("application/json").content("{\"openingAmount\":10.00}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/billing/cash-shifts").header("Authorization", "Bearer reader-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer reader-token")
                        .contentType("application/json")
                        .content("{\"type\":\"INCOME\",\"amount\":10.00,\"concept\":\"Agua\"}"))
                .andExpect(status().isForbidden());

        verify(cashShiftService, never()).open(any(), any());
        verify(cashShiftService, never()).listShifts(any(), eq(true), any());
    }

    @Test
    void openUsesJwtUserAndIgnoresUserIdInTheBody() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID bodyUserId = UUID.randomUUID();
        authenticate("cashier-token", actorId, "BILLING_CASH_MANAGE");
        when(cashShiftService.open(eq(actorId), any())).thenReturn(shift(UUID.randomUUID(), actorId));

        mockMvc.perform(post("/api/v1/billing/cash-shifts")
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","openingAmount":100.00,"notes":"apertura"}
                                """.formatted(bodyUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(actorId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        ArgumentCaptor<OpenCashShiftRequest> request = ArgumentCaptor.forClass(OpenCashShiftRequest.class);
        verify(cashShiftService).open(eq(actorId), request.capture());
        verify(cashShiftService, never()).open(eq(bodyUserId), any());
        assertThat(request.getValue().openingAmount()).isEqualByComparingTo("100.00");
        assertThat(request.getValue().notes()).isEqualTo("apertura");
    }

    @Test
    void rejectsInvalidPayloadsBeforeCallingService() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        authenticate("cashier-token", actorId, "BILLING_CASH_MANAGE");

        mockMvc.perform(post("/api/v1/billing/cash-shifts")
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content("{\"openingAmount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.openingAmount").exists());
        mockMvc.perform(post("/api/v1/billing/cash-shifts")
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content("{\"openingAmount\":10.555}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.openingAmount").exists());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/close", shiftId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json").content("{\"countedAmount\":-0.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.countedAmount").exists());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"type\":\"INCOME\",\"amount\":0,\"concept\":\"Agua\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"amount\":10.00,\"concept\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.concept").exists());
        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("{\"type\":\"BITCOIN\",\"amount\":10.00,\"concept\":\"Agua\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request is malformed or contains an invalid value"));

        verify(cashShiftService, never()).open(any(), any());
        verify(cashShiftService, never()).close(any(), any(), any());
        verify(cashShiftService, never()).addMovement(any(), any(), any());
    }

    @Test
    void listForwardsPaginationAndReadAllFlagFromJwt() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        authenticate("admin-token", actorId, "BILLING_CASH_MANAGE", "BILLING_CASH_READ_ALL");
        when(cashShiftService.listShifts(eq(actorId), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(shift(shiftId, actorId))));
        when(cashShiftService.listMovements(eq(actorId), eq(true), eq(shiftId), any()))
                .thenReturn(new PageImpl<>(List.of(movement(shiftId, actorId))));

        mockMvc.perform(get("/api/v1/billing/cash-shifts")
                        .header("Authorization", "Bearer admin-token")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(shiftId.toString()));
        mockMvc.perform(get("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer admin-token")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].concept").value("Agua"));

        ArgumentCaptor<Pageable> shifts = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> movements = ArgumentCaptor.forClass(Pageable.class);
        verify(cashShiftService).listShifts(eq(actorId), eq(true), shifts.capture());
        verify(cashShiftService).listMovements(eq(actorId), eq(true), eq(shiftId), movements.capture());
        assertThat(shifts.getValue().getPageNumber()).isZero();
        assertThat(shifts.getValue().getPageSize()).isEqualTo(20);
        assertThat(movements.getValue().getPageNumber()).isEqualTo(1);
        assertThat(movements.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listWithoutReadAllDoesNotGrantGlobalVisibility() throws Exception {
        UUID actorId = UUID.randomUUID();
        authenticate("cashier-token", actorId, "BILLING_CASH_MANAGE");
        when(cashShiftService.listShifts(eq(actorId), eq(false), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/billing/cash-shifts").header("Authorization", "Bearer cashier-token"))
                .andExpect(status().isOk());

        verify(cashShiftService).listShifts(eq(actorId), eq(false), any());
    }

    @Test
    void movementUsesJwtUser() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID bodyUserId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        authenticate("cashier-token", actorId, "BILLING_CASH_MANAGE");
        when(cashShiftService.addMovement(eq(actorId), eq(shiftId), any())).thenReturn(
                new CashMovementResponse(UUID.randomUUID(), shiftId, CashMovementType.EXPENSE,
                        new BigDecimal("15.50"), "Insumos", actorId, NOW));

        mockMvc.perform(post("/api/v1/billing/cash-shifts/{shiftId}/movements", shiftId)
                        .header("Authorization", "Bearer cashier-token")
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","type":"EXPENSE","amount":15.50,"concept":"Insumos"}
                                """.formatted(bodyUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByUserId").value(actorId.toString()))
                .andExpect(jsonPath("$.type").value("EXPENSE"));

        ArgumentCaptor<CreateCashMovementRequest> request = ArgumentCaptor.forClass(CreateCashMovementRequest.class);
        verify(cashShiftService).addMovement(eq(actorId), eq(shiftId), request.capture());
        verify(cashShiftService, never()).addMovement(eq(bodyUserId), any(), any());
        assertThat(request.getValue().type()).isEqualTo(CashMovementType.EXPENSE);
        assertThat(request.getValue().amount()).isEqualByComparingTo("15.50");
        assertThat(request.getValue().concept()).isEqualTo("Insumos");
    }

    private void authenticate(String token, UUID userId, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authorities)));
    }

    private CashShiftResponse shift(UUID id, UUID userId) {
        return new CashShiftResponse(id, userId, CashShiftStatus.OPEN, new BigDecimal("100.00"),
                NOW, null, new BigDecimal("100.00"), null, null, "apertura", null);
    }

    private CashMovementResponse movement(UUID shiftId, UUID userId) {
        return new CashMovementResponse(UUID.randomUUID(), shiftId, CashMovementType.INCOME,
                new BigDecimal("10.00"), "Agua", userId, NOW);
    }
}
