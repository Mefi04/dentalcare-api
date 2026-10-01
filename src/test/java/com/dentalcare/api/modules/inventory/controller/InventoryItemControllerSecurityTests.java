package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.service.InventoryItemService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryItemController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class InventoryItemControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryItemService inventoryItemService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void listRejectsUnauthenticatedCallerWith401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRejectsAuthenticatedCallerWithoutInventoryReadPermissionWith403() throws Exception {
        when(jwtService.parseAccessToken("unauthorized-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("PATIENT_READ")));

        mockMvc.perform(get("/api/v1/inventory/items").header("Authorization", "Bearer unauthorized-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllowsCallerWithInventoryReadPermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("read-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_READ")));

        InventoryItemResponse itemResponse = sampleConsumableResponse(id);
        when(inventoryItemService.findAll(null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(itemResponse)));

        mockMvc.perform(get("/api/v1/inventory/items").header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].code").value("CON-001"))
                .andExpect(jsonPath("$.content[0].type").value("CONSUMABLE"));

        verify(inventoryItemService).findAll(null, null, null, null, 0, 20);
    }

    @Test
    void getByIdReturnsItemWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("read-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_READ")));

        when(inventoryItemService.findById(id)).thenReturn(sampleInstrumentResponse(id));

        mockMvc.perform(get("/api/v1/inventory/items/" + id).header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("INS-001"))
                .andExpect(jsonPath("$.type").value("INSTRUMENT"))
                .andExpect(jsonPath("$.location").value("Gabinete A"));
    }

    @Test
    void getByIdReturns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("read-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_READ")));

        when(inventoryItemService.findById(id))
                .thenThrow(new ResourceNotFoundException("Inventory item not found"));

        mockMvc.perform(get("/api/v1/inventory/items/" + id).header("Authorization", "Bearer read-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Inventory item not found"));
    }

    @Test
    void createRejectsCallerWithoutInventoryWritePermissionWith403() throws Exception {
        when(jwtService.parseAccessToken("read-only-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_READ")));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer read-only-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CON-001",
                                  "name": "Guantes",
                                  "type": "CONSUMABLE",
                                  "category": "Protección",
                                  "unit": "Caja",
                                  "currentStock": 10,
                                  "minimumStock": 5
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAllowsCallerWithInventoryWritePermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        when(inventoryItemService.create(any(CreateInventoryItemRequest.class)))
                .thenReturn(sampleConsumableResponse(id));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CON-001",
                                  "name": "Guantes de nitrilo",
                                  "type": "CONSUMABLE",
                                  "category": "Protección personal",
                                  "unit": "Caja",
                                  "currentStock": 18,
                                  "minimumStock": 6,
                                  "expirationDate": "2027-08-31"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/inventory/items/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("CON-001"));
    }

    @Test
    void createReturns400OnValidationFailure() throws Exception {
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": "",
                                  "type": null,
                                  "category": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void createReturns409OnDuplicateCode() throws Exception {
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        when(inventoryItemService.create(any(CreateInventoryItemRequest.class)))
                .thenThrow(new ConflictException("An inventory item with code 'CON-001' already exists"));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CON-001",
                                  "name": "Guantes",
                                  "type": "CONSUMABLE",
                                  "category": "Protección",
                                  "unit": "Caja",
                                  "currentStock": 10,
                                  "minimumStock": 5
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An inventory item with code 'CON-001' already exists"));
    }

    @Test
    void updateAllowsCallerWithInventoryWritePermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        when(inventoryItemService.update(eq(id), any(UpdateInventoryItemRequest.class)))
                .thenReturn(sampleConsumableResponse(id));

        mockMvc.perform(put("/api/v1/inventory/items/" + id)
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Guantes actualizados",
                                  "category": "Protección",
                                  "unit": "Caja",
                                  "minimumStock": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateStatusAllowsCallerWithInventoryWritePermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        InventoryItemResponse updatedResponse = new InventoryItemResponse(
                id, "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.INACTIVE, "Caja", 18, 6, null,
                null, null, null, NOW, NOW
        );
        when(inventoryItemService.updateStatus(eq(id), eq(InventoryItemStatus.INACTIVE)))
                .thenReturn(updatedResponse);

        mockMvc.perform(patch("/api/v1/inventory/items/" + id + "/status")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivateReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseAccessToken("write-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("INVENTORY_WRITE")));

        mockMvc.perform(delete("/api/v1/inventory/items/" + id)
                        .header("Authorization", "Bearer write-token"))
                .andExpect(status().isNoContent());

        verify(inventoryItemService).deactivate(id);
    }

    private InventoryItemResponse sampleConsumableResponse(UUID id) {
        return new InventoryItemResponse(
                id,
                "CON-001",
                "Guantes de nitrilo",
                "Guantes talla M",
                InventoryItemType.CONSUMABLE,
                "Protección personal",
                InventoryItemStatus.ACTIVE,
                "Caja",
                18,
                6,
                LocalDate.of(2027, 8, 31),
                null, null, null,
                NOW, NOW
        );
    }

    private InventoryItemResponse sampleInstrumentResponse(UUID id) {
        return new InventoryItemResponse(
                id,
                "INS-001",
                "Espejo dental",
                "Espejo de exploración plano",
                InventoryItemType.INSTRUMENT,
                "Diagnóstico",
                InventoryItemStatus.ACTIVE,
                null, null, null, null,
                "Gabinete A",
                24,
                18,
                NOW, NOW
        );
    }
}
