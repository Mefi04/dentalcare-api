package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.service.InventoryMovementService;
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

import java.time.Instant;
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

@WebMvcTest(controllers = InventoryMovementController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class InventoryMovementControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InventoryMovementService movementService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void registerRejectsUnauthenticatedCaller() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items/" + UUID.randomUUID() + "/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ENTRY","quantity":5}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsCallerWithoutWritePermission() throws Exception {
        authenticate("read-token", UUID.randomUUID(), "INVENTORY_READ");

        mockMvc.perform(post("/api/v1/inventory/items/" + UUID.randomUUID() + "/movements")
                        .header("Authorization", "Bearer read-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ENTRY","quantity":5}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerDerivesResponsibleUserFromJwtPrincipal() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InventoryMovementResponse response = response(itemId, userId);
        authenticate("write-token", userId, "INVENTORY_WRITE");
        when(movementService.register(eq(itemId), any(CreateInventoryMovementRequest.class), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/items/" + itemId + "/movements")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"ENTRY",
                                  "quantity":5,
                                  "observation":"Compra inicial",
                                  "reference":"REF-73",
                                  "performedBy":"spoofed-user"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performedById").value(userId.toString()))
                .andExpect(jsonPath("$.stockBefore").value(10))
                .andExpect(jsonPath("$.stockAfter").value(15));

        verify(movementService).register(eq(itemId), any(CreateInventoryMovementRequest.class), eq(userId));
    }

    @Test
    void registerRejectsNonPositiveQuantity() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");

        mockMvc.perform(post("/api/v1/inventory/items/" + UUID.randomUUID() + "/movements")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXIT","quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be greater than zero"));
    }

    @Test
    void kardexRequiresReadPermissionAndAllowsCombinedFilters() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate("read-token", userId, "INVENTORY_READ");
        when(movementService.findAll(itemId, InventoryMovementType.ENTRY, userId,
                NOW.minusSeconds(60), NOW, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response(itemId, userId))));

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .header("Authorization", "Bearer read-token")
                        .param("itemId", itemId.toString())
                        .param("type", "ENTRY")
                        .param("performedBy", userId.toString())
                        .param("from", NOW.minusSeconds(60).toString())
                        .param("to", NOW.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.content[0].type").value("ENTRY"));
    }

    private void authenticate(String token, UUID userId, String authority) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authority)));
    }

    private InventoryMovementResponse response(UUID itemId, UUID userId) {
        return new InventoryMovementResponse(
                UUID.randomUUID(), itemId, "CON-001", "Guantes", InventoryItemType.CONSUMABLE,
                InventoryMovementType.ENTRY, 5, 10, 15, null, null,
                userId, "operator", "Inventory Operator", "Compra inicial", "REF-73", NOW);
    }
}
