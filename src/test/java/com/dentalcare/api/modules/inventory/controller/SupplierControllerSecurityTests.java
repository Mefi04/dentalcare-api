package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.service.SupplierService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SupplierController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class SupplierControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SupplierService supplierService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void findAllRejectsUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/suppliers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAllAcceptsCallerWithReadPermission() throws Exception {
        authenticate("read-token", UUID.randomUUID(), "INVENTORY_READ");
        SupplierResponse response = supplierResponse(UUID.randomUUID(), "Dental Supplies");
        when(supplierService.findAll(null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/inventory/suppliers")
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Dental Supplies"));
    }

    @Test
    void createRejectsCallerWithoutWritePermission() throws Exception {
        authenticate("read-token", UUID.randomUUID(), "INVENTORY_READ");

        mockMvc.perform(post("/api/v1/inventory/suppliers")
                        .header("Authorization", "Bearer read-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Supplier"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAcceptsCallerWithWritePermission() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");
        UUID id = UUID.randomUUID();
        when(supplierService.create(any(CreateSupplierRequest.class)))
                .thenReturn(supplierResponse(id, "New Supplier"));

        mockMvc.perform(post("/api/v1/inventory/suppliers")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"New Supplier",
                                  "contactName":"Ana Gomez",
                                  "phone":"12345678",
                                  "email":"ana@vendor.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("New Supplier"));

        verify(supplierService).create(any(CreateSupplierRequest.class));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");

        mockMvc.perform(post("/api/v1/inventory/suppliers")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Supplier name is required"));
    }

    @Test
    void updateAcceptsCallerWithWritePermission() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");
        UUID id = UUID.randomUUID();
        when(supplierService.update(eq(id), any(UpdateSupplierRequest.class)))
                .thenReturn(supplierResponse(id, "Updated Supplier"));

        mockMvc.perform(put("/api/v1/inventory/suppliers/" + id)
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Updated Supplier",
                                  "contactName":"Ana Gomez",
                                  "phone":"12345678",
                                  "email":"ana@vendor.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Supplier"));
    }

    @Test
    void updateStatusAcceptsCallerWithWritePermission() throws Exception {
        authenticate("write-token", UUID.randomUUID(), "INVENTORY_WRITE");
        UUID id = UUID.randomUUID();
        SupplierResponse inactiveResponse = new SupplierResponse(
                id, "Vendor", "Contact", "1111", "v@mail.com", "Address", "Notes",
                SupplierStatus.INACTIVE, false, NOW, NOW
        );
        when(supplierService.updateStatus(eq(id), any(UpdateSupplierStatusRequest.class)))
                .thenReturn(inactiveResponse);

        mockMvc.perform(patch("/api/v1/inventory/suppliers/" + id + "/status")
                        .header("Authorization", "Bearer write-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.active").value(false));
    }

    private void authenticate(String token, UUID userId, String authority) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(userId, List.of(authority)));
    }

    private SupplierResponse supplierResponse(UUID id, String name) {
        return new SupplierResponse(
                id, name, "Contact Name", "12345678", "mail@vendor.com",
                "Zone 10", "Notes", SupplierStatus.ACTIVE, true, NOW, NOW
        );
    }
}
