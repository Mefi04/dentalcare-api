package com.dentalcare.api.modules.audit.controller;

import com.dentalcare.api.config.*;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.audit.model.AuditResult;
import com.dentalcare.api.modules.audit.service.AuditQueryService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.*;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=AuditEventController.class,properties="FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class,CorsConfig.class,JwtAuthenticationFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,GlobalExceptionHandler.class})
class AuditEventControllerSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuditQueryService service;
    @MockitoBean JwtService jwt;
    private final UUID actor=UUID.randomUUID();

    @Test void auditReadRequiresAuthenticationAndExplicitAuthority() throws Exception {
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
        token("staff","BILLING_READ");
        mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer staff")).andExpect(status().isForbidden());
        token("admin","AUDIT_READ");
        when(service.search(any(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer admin")).andExpect(status().isOk());
    }

    @Test void getDelegatesFiltersAndPagination() throws Exception {
        token("admin","AUDIT_READ");
        when(service.search(any(),any(),any(),any(),any(),any(),any(),eq(2),eq(10))).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer admin")
                .param("from","2026-01-01T00:00:00Z").param("to","2026-01-31T00:00:00Z")
                .param("actorUserId",actor.toString()).param("module","BILLING").param("action","BILLING_PAYMENT_CREATED")
                .param("result","SUCCESS").param("q","payment").param("page","2").param("size","10"))
                .andExpect(status().isOk());
        verify(service).search(Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-01-31T00:00:00Z"),actor,
                "BILLING","BILLING_PAYMENT_CREATED",AuditResult.SUCCESS,"payment",2,10);
    }

    @Test void rejectsInvalidPaginationAndDateRange() throws Exception {
        token("admin","AUDIT_READ");
        for(String[] params: List.of(new String[]{"page","-1"},new String[]{"size","0"},new String[]{"size","101"}))
            mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer admin").param(params[0],params[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer admin")
                .param("from","2026-02-01T00:00:00Z").param("to","2026-01-01T00:00:00Z")).andExpect(status().isBadRequest());
    }

    @Test void onlyGetIsAnImplementedAuditRoute() {
        for (Method method : AuditEventController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) continue;
            org.junit.jupiter.api.Assertions.assertFalse(method.isAnnotationPresent(PostMapping.class));
            org.junit.jupiter.api.Assertions.assertFalse(method.isAnnotationPresent(PutMapping.class));
            org.junit.jupiter.api.Assertions.assertFalse(method.isAnnotationPresent(PatchMapping.class));
            org.junit.jupiter.api.Assertions.assertFalse(method.isAnnotationPresent(DeleteMapping.class));
        }
    }
    private void token(String raw,String... authorities){when(jwt.parseAccessToken(raw)).thenReturn(new JwtService.AccessTokenClaims(actor,List.of(authorities)));}
}
