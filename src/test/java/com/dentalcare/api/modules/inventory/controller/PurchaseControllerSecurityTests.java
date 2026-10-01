package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseItemResponse;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.service.PurchaseService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PurchaseController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PurchaseControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PurchaseService purchaseService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void findAllRejectsUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/purchases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAllAcceptsCallerWithReadPermission() throws Exception {
        authenticate("read-token", UUID.randomUUID(), "INVENTORY_READ");
        PurchaseResponse response = purchaseResponse(UUID.randomUUID(), "PUR-2026-000001", PurchaseStatus.PENDING);
        when(purchaseService.findAll(null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/inventory/purchases")
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("PUR-2026-000001"));
    }

    @Test
    void createRejectsCallerWithoutWritePermission() throws Exception {
        authenticate("read-token", UUID.randomUUID(), "INVENTORY_READ");

        mockMvc.perform(post("/api/v1/inventory/purchases")
                        .header("Authorization", "Bearer read-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"00000000-0000-0000-0000-000000000001",
                                  "purchaseDate":"2026-10-01",
                                  "items":[
                                    {
                                      "consumableId":"00000000-0000-0000-0000-000000000002",
                                      "quantity":1,
                                      "unitCost":10.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDerivesResponsibleUserFromJwtPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        authenticate("write-token", userId, "INVENTORY_WRITE");
        PurchaseResponse response = purchaseResponse(purchaseId, "PUR-2026-000001", PurchaseStatus.PENDING);
        when(purchaseService.create(any(CreatePurchaseRequest.class), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/purchases")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"00000000-0000-0000-0000-000000000001",
                                  "purchaseDate":"2026-10-01",
                                  "items":[
                                    {
                                      "consumableId":"00000000-0000-0000-0000-000000000002",
                                      "quantity":5,
                                      "unitCost":10.50
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PUR-2026-000001"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(purchaseService).create(any(CreatePurchaseRequest.class), eq(userId));
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");

        mockMvc.perform(post("/api/v1/inventory/purchases")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"00000000-0000-0000-0000-000000000001",
                                  "purchaseDate":"2026-10-01",
                                  "items":[]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("Purchase items cannot be empty"));
    }

    @Test
    void receiveAcceptsCallerWithWritePermissionAndDerivesUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        authenticate("write-token", userId, "INVENTORY_WRITE");
        PurchaseResponse response = purchaseResponse(purchaseId, "PUR-2026-000001", PurchaseStatus.RECEIVED);
        when(purchaseService.receive(eq(purchaseId), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/purchases/" + purchaseId + "/receive")
                        .header("Authorization", "Bearer write-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(purchaseService).receive(eq(purchaseId), eq(userId));
    }

    private void authenticate(String token, UUID userId, String authority) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authority)));
    }

    private PurchaseResponse purchaseResponse(UUID id, String code, PurchaseStatus status) {
        return new PurchaseResponse(
                id, code, UUID.randomUUID(), "Supplier Name", status,
                LocalDate.parse("2026-10-01"), "REF-1", "Obs", new BigDecimal("52.50"),
                UUID.randomUUID(), "Creator Name", NOW,
                status == PurchaseStatus.RECEIVED ? UUID.randomUUID() : null,
                status == PurchaseStatus.RECEIVED ? "Receiver Name" : null,
                status == PurchaseStatus.RECEIVED ? NOW : null,
                List.of(new PurchaseItemResponse(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "CON-001", "Item", "caja", 5, new BigDecimal("10.50"), new BigDecimal("52.50")
                ))
        );
    }
}
