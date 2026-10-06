package com.dentalcare.api.modules.contactinquiries.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.contactinquiries.dto.response.ContactInquiryAcknowledgement;
import com.dentalcare.api.modules.contactinquiries.dto.response.ContactInquiryResponse;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryReason;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryStatus;
import com.dentalcare.api.modules.contactinquiries.service.ContactInquiryService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicContactInquiryController.class, ContactInquiryController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class ContactInquiryControllerSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContactInquiryService service;

    @MockitoBean
    private JwtService jwt;

    @ParameterizedTest
    @ValueSource(strings = {
            "GENERAL",
            "SERVICES",
            "PROFESSIONALS",
            "LOCATIONS",
            "APPOINTMENT_HELP",
            "ACCOUNT_ACTIVATION",
            "OTHER"
    })
    @DisplayName("accepts all 7 contract reasons on anonymous public endpoint")
    void anonymousCanPostWithAllValidReasons(String reason) throws Exception {
        when(service.create(any())).thenReturn(new ContactInquiryAcknowledgement(true, "received"));

        String body = String.format("""
                {
                  "name": "Ana Perez",
                  "email": "ana@test.com",
                  "phone": "5555-1234",
                  "reason": "%s",
                  "message": "Consulta detallada para %s",
                  "privacyAccepted": true
                }
                """, reason, reason);

        mvc.perform(post("/api/v1/public/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "general",
            "servicios",
            "odontologos",
            "sucursales",
            "cita",
            "activar",
            "otro"
    })
    @DisplayName("accepts frontend form keys seamlessly via case-tolerant mapping")
    void anonymousCanPostWithFrontendFormKeys(String frontendKey) throws Exception {
        when(service.create(any())).thenReturn(new ContactInquiryAcknowledgement(true, "received"));

        String body = String.format("""
                {
                  "name": "Carlos Gomez",
                  "email": "carlos@test.com",
                  "phone": "5555-9876",
                  "reason": "%s",
                  "message": "Consulta con clave de formulario",
                  "privacyAccepted": true
                }
                """, frontendKey);

        mvc.perform(post("/api/v1/public/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INVALID_REASON",
            "PRICING",
            "UNKNOWN",
            "citas_urgentes",
            ""
    })
    @DisplayName("rejects nonexistent or removed reasons with 400 Bad Request")
    void rejectsInvalidOrRemovedReasons(String invalidReason) throws Exception {
        String body = String.format("""
                {
                  "name": "Pedro",
                  "email": "pedro@test.com",
                  "phone": "5555-1234",
                  "reason": "%s",
                  "message": "Mensaje de prueba",
                  "privacyAccepted": true
                }
                """, invalidReason);

        mvc.perform(post("/api/v1/public/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects null reason and unaccepted privacy with 400 Bad Request")
    void rejectsNullReasonAndPrivacyNotAccepted() throws Exception {
        String missingReason = """
                {
                  "name": "Pedro",
                  "email": "pedro@test.com",
                  "message": "Mensaje",
                  "privacyAccepted": true
                }
                """;
        mvc.perform(post("/api/v1/public/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingReason))
                .andExpect(status().isBadRequest());

        String privacyFalse = """
                {
                  "name": "Pedro",
                  "email": "pedro@test.com",
                  "reason": "GENERAL",
                  "message": "Mensaje",
                  "privacyAccepted": false
                }
                """;
        mvc.perform(post("/api/v1/public/contact-inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(privacyFalse))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("maintains security: contact-inquiries management requires ROLE_ADMINISTRATOR")
    void onlyAdministratorCanManage() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        // Anonymous cannot access management endpoints
        mvc.perform(get("/api/v1/contact-inquiries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/contact-inquiries")).andExpect(status().isUnauthorized());

        // Dentist cannot access
        token("dentist", actor, "ROLE_DENTIST");
        mvc.perform(get("/api/v1/contact-inquiries").header("Authorization", "Bearer dentist"))
                .andExpect(status().isForbidden());

        // Administrator can access
        token("admin", actor, "ROLE_ADMINISTRATOR");
        var response = new ContactInquiryResponse(id, "Ana", "ana@test.com", null,
                ContactInquiryReason.GENERAL, "Hola", ContactInquiryStatus.NEW,
                Instant.now(), Instant.now());
        when(service.findAll(null, 0, 20)).thenReturn(new PageImpl<>(List.of(response)));
        when(service.updateStatus(eq(id), any(), eq(actor))).thenReturn(response);

        mvc.perform(get("/api/v1/contact-inquiries").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/contact-inquiries/{id}/status", id)
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk());
    }

    private void token(String raw, UUID id, String... auth) {
        when(jwt.parseAccessToken(raw)).thenReturn(new JwtService.AccessTokenClaims(id, List.of(auth)));
    }
}
