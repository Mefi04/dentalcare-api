package com.dentalcare.api.modules.settings.controller;

import com.dentalcare.api.config.*;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.settings.dto.response.*;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import com.dentalcare.api.modules.settings.service.*;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.*;
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
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={ClinicSettingsController.class,ProcedureCatalogController.class},properties="FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class,CorsConfig.class,JwtAuthenticationFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,GlobalExceptionHandler.class})
class SettingsControllerSecurityTests {
    @Autowired MockMvc mvc; @MockitoBean ClinicSettingsService clinic; @MockitoBean ProcedureCatalogService catalog; @MockitoBean JwtService jwt;
    private final UUID actor=UUID.randomUUID(); private final Instant now=Instant.parse("2026-10-04T12:00:00Z");
    @Test void unauthenticatedRequestsReturn401() throws Exception {mvc.perform(get("/api/v1/settings/clinic")).andExpect(status().isUnauthorized());}
    @Test void callerWithoutSettingsPermissionReturns403() throws Exception {token("dentist","ROLE_DENTIST");mvc.perform(get("/api/v1/settings/catalog").header("Authorization","Bearer dentist")).andExpect(status().isForbidden());}
    @Test void administratorPermissionCanReadClinicAndCatalog() throws Exception {token("read","SETTINGS_READ");
        when(clinic.get()).thenReturn(new ClinicSettingsResponse((short)1,null,null,null,null,null,null,null,null,null,now,now));
        when(catalog.findAll(null,null,null,0,20)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/settings/clinic").header("Authorization","Bearer read")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/v1/settings/catalog").header("Authorization","Bearer read")).andExpect(status().isOk());}
    @Test void catalogListDelegatesSearchFiltersAndPagination() throws Exception {token("read","SETTINGS_READ");
        when(catalog.findAll("clean","Prevention",ProcedureCatalogItemStatus.ACTIVE,2,15)).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/settings/catalog").param("search","clean").param("category","Prevention").param("status","ACTIVE")
                .param("page","2").param("size","15").header("Authorization","Bearer read")).andExpect(status().isOk());
        verify(catalog).findAll("clean","Prevention",ProcedureCatalogItemStatus.ACTIVE,2,15);}
    @Test void readOnlyCallerCannotModify() throws Exception {token("read","SETTINGS_READ");mvc.perform(put("/api/v1/settings/clinic").header("Authorization","Bearer read")
        .contentType(MediaType.APPLICATION_JSON).content(clinicBody())).andExpect(status().isForbidden());}
    @Test void clinicUpdateUsesJwtActorAndRejectsActorPayload() throws Exception {token("write","SETTINGS_WRITE");
        when(clinic.update(any(),eq(actor))).thenReturn(new ClinicSettingsResponse((short)1,"DentalCare","548796-2","22224500",null,null,null,null,null,actor,now,now));
        mvc.perform(put("/api/v1/settings/clinic").header("Authorization","Bearer write").contentType(MediaType.APPLICATION_JSON)
            .content(clinicBody().replace("}",",\"updatedBy\":\""+UUID.randomUUID()+"\"}"))).andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value(actor.toString()));
        verify(clinic).update(any(),eq(actor));}
    @Test void catalogCreateUsesJwtActorAndReturns201() throws Exception {token("write","SETTINGS_WRITE");UUID id=UUID.randomUUID();
        when(catalog.create(any(),eq(actor))).thenReturn(new ProcedureCatalogItemResponse(id,"PROC-1","Evaluation","Consultation",30,new BigDecimal("150.00"),ProcedureCatalogItemStatus.ACTIVE,actor,actor,now,now));
        mvc.perform(post("/api/v1/settings/catalog").header("Authorization","Bearer write").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"PROC-1\",\"name\":\"Evaluation\",\"category\":\"Consultation\",\"durationMinutes\":30,\"basePrice\":150.00}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location","http://localhost/api/v1/settings/catalog/"+id));}
    @Test void invalidClinicAndCatalogPayloadsReturn400() throws Exception {token("write","SETTINGS_WRITE");
        mvc.perform(put("/api/v1/settings/clinic").header("Authorization","Bearer write").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.tradeName").exists());
        mvc.perform(post("/api/v1/settings/catalog").header("Authorization","Bearer write").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"P\",\"name\":\"N\",\"category\":\"C\",\"durationMinutes\":1,\"basePrice\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.durationMinutes").exists()).andExpect(jsonPath("$.fieldErrors.basePrice").exists());}
    private void token(String raw,String... authorities){when(jwt.parseAccessToken(raw)).thenReturn(new JwtService.AccessTokenClaims(actor,List.of(authorities)));}
    private String clinicBody(){return "{\"tradeName\":\"DentalCare\",\"nit\":\"548796-2\",\"phone\":\"22224500\"}";}
}
