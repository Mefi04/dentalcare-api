package com.dentalcare.api.modules.users.controller;

import com.dentalcare.api.config.*;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.users.dto.response.ProfessionalPublicProfileResponse;
import com.dentalcare.api.modules.users.service.ProfessionalPublicProfileService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.*;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ProfessionalPublicProfileController.class, properties="FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class,CorsConfig.class,JwtAuthenticationFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,GlobalExceptionHandler.class})
class ProfessionalPublicProfileControllerSecurityTests {
    @Autowired MockMvc mvc; @MockitoBean ProfessionalPublicProfileService service; @MockitoBean JwtService jwt;
    @Test void administrationRequiresAdministratorAndUsesAuthenticatedActor() throws Exception {
        UUID userId=UUID.randomUUID(), actor=UUID.randomUUID(), profileId=UUID.randomUUID();
        mvc.perform(get("/api/v1/users/{id}/public-profile",userId)).andExpect(status().isUnauthorized());
        token("dentist",actor,"ROLE_DENTIST");
        mvc.perform(get("/api/v1/users/{id}/public-profile",userId).header("Authorization","Bearer dentist")).andExpect(status().isForbidden());
        token("admin",actor,"ROLE_ADMINISTRATOR");
        var response=new ProfessionalPublicProfileResponse(profileId,userId,"Dra. López","COL-1","Ortodoncia","Bio",8,"Español",null,true);
        when(service.findByUserId(userId)).thenReturn(response); when(service.upsert(eq(userId),any(),eq(actor))).thenReturn(response);
        mvc.perform(get("/api/v1/users/{id}/public-profile",userId).header("Authorization","Bearer admin")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/{id}/public-profile",userId).header("Authorization","Bearer admin").contentType("application/json")
                .content("{\"professionalRegistration\":\"COL-1\",\"specialty\":\"Ortodoncia\",\"summary\":\"Bio\",\"yearsExperience\":8,\"publicVisible\":true}"))
                .andExpect(status().isOk());
        verify(service).upsert(eq(userId),any(),eq(actor));
    }
    @Test void invalidProfilePayloadIsRejected() throws Exception { UUID userId=UUID.randomUUID(); token("admin",UUID.randomUUID(),"ROLE_ADMINISTRATOR");
        mvc.perform(put("/api/v1/users/{id}/public-profile",userId).header("Authorization","Bearer admin").contentType("application/json")
                .content("{\"professionalRegistration\":\"\",\"specialty\":\"\",\"summary\":\"\",\"yearsExperience\":-1,\"photoUrl\":\"ftp://invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.professionalRegistration").exists()); }
    private void token(String raw,UUID actor,String... authorities){when(jwt.parseAccessToken(raw)).thenReturn(new JwtService.AccessTokenClaims(actor,List.of(authorities)));}
}
