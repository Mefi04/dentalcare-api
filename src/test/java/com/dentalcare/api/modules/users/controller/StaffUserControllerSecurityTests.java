package com.dentalcare.api.modules.users.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.users.dto.response.StaffUserResponse;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.service.StaffUserService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {StaffUserController.class, RoleController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class StaffUserControllerSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean StaffUserService service;
    @MockitoBean JwtService jwt;

    @Test void administratorCanListUsersAndRoles() throws Exception {
        token("admin", "ROLE_ADMINISTRATOR");
        when(service.search(anyInt(), anyInt(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(service.findActiveRoles()).thenReturn(List.of());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer admin")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer admin")).andExpect(status().isOk());
    }

    @Test void nonAdministratorReceives403() throws Exception {
        token("dentist", "ROLE_DENTIST");
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer dentist"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test void missingAndInvalidTokensReceive401() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        when(jwt.parseAccessToken("invalid")).thenThrow(new IllegalArgumentException("bad token"));
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test void creationResponseContainsTemporaryPasswordButNormalResponseDoesNotContainHash() throws Exception {
        token("admin", "ROLE_ADMINISTRATOR");
        UUID id = UUID.randomUUID();
        StaffUserResponse user = new StaffUserResponse(id, "Laura", "staff-id", "laura@example.com",
                UserStatus.PENDING_ACTIVATION, List.of("SECRETARY"), null, Instant.now(), Instant.now());
        when(service.create(any())).thenReturn(new com.dentalcare.api.modules.users.dto.response.CreateStaffUserResponse(user, "Temp!123"));
        when(service.findById(id)).thenReturn(user);
        String body = "{\"fullName\":\"Laura\",\"cui\":\"1234567890123\",\"email\":\"laura@example.com\",\"roleCode\":\"SECRETARY\"}";
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer admin")
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.temporaryPassword").value("Temp!123"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/users/{id}", id).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private void token(String raw, String... authorities) {
        when(jwt.parseAccessToken(raw)).thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }
}
