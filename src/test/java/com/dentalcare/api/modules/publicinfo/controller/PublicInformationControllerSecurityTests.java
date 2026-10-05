package com.dentalcare.api.modules.publicinfo.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.service.PublicInformationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PublicInformationController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class PublicInformationControllerSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean PublicInformationService service;
    @MockitoBean JwtService jwt;

    @Test void anonymousVisitorGetsOnlyWhitelistedClinicFields() throws Exception {
        when(service.getClinic()).thenReturn(new PublicClinicResponse("DentalCare", "+502 2222-4500",
                "info@dentalcare.test", "Zone 10", "Guatemala", "Mon-Fri 08:00-17:00"));

        mvc.perform(get("/api/v1/public/clinic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("DentalCare"))
                .andExpect(jsonPath("$.phone").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.address").exists())
                .andExpect(jsonPath("$.city").exists())
                .andExpect(jsonPath("$.businessHours").exists())
                .andExpect(jsonPath("$.nit").doesNotExist())
                .andExpect(jsonPath("$.receiptPrefix").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test void anonymousVisitorGetsOnlyActiveServicesProjection() throws Exception {
        when(service.getServices()).thenReturn(List.of(new PublicServiceResponse("CLEANING", "Cleaning", "Prevention", 30)));

        mvc.perform(get("/api/v1/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CLEANING"))
                .andExpect(jsonPath("$[0].name").value("Cleaning"))
                .andExpect(jsonPath("$[0].category").value("Prevention"))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].basePrice").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].updatedBy").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test void publicRoutesDoNotAllowAnonymousMutationsAndSettingsStayProtected() throws Exception {
        mvc.perform(post("/api/v1/public/clinic")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/public/clinic")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/public/services")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/public/services")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/settings/clinic")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/settings/catalog")).andExpect(status().isUnauthorized());
    }
}
