package com.dentalcare.api.security;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TestSecurityController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class})
class AuthorizationIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void roleProtectedEndpointAcceptsRolePrefixedAuthority() throws Exception {
        token("admin", "ROLE_ADMINISTRATOR");
        mockMvc.perform(get("/test/security/admin").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void roleProtectedEndpointRejectsUnprefixedRoleCodeWithJson403() throws Exception {
        token("unprefixed", "ADMINISTRATOR");
        mockMvc.perform(get("/test/security/admin").header("Authorization", "Bearer unprefixed"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(jsonPath("$.path").value("/test/security/admin"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void authorityProtectedEndpointAcceptsPermissionCode() throws Exception {
        token("permission", "PATIENT_READ");
        mockMvc.perform(get("/test/security/patient-read").header("Authorization", "Bearer permission"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithoutRequiredAuthorityReceives403() throws Exception {
        token("other", "ROLE_DENTIST");
        mockMvc.perform(get("/test/security/patient-read").header("Authorization", "Bearer other"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenReceives401() throws Exception {
        mockMvc.perform(get("/test/security/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidTokenReceives401() throws Exception {
        when(jwtService.parseAccessToken("invalid")).thenThrow(new IllegalArgumentException("invalid token"));
        mockMvc.perform(get("/test/security/admin").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token)).thenReturn(
                new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

}
