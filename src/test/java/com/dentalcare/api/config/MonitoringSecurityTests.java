package com.dentalcare.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MonitoringSecurityTests.Probe.class)
@Import({MonitoringSecurityConfig.class, MonitoringSecurityTests.Probe.class})
class MonitoringSecurityTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.dentalcare.api.security.jwt.JwtService jwtService;
    @Autowired MockMvc mvc;
    @org.springframework.web.bind.annotation.RestController
    static class Probe {
        @org.springframework.web.bind.annotation.GetMapping("/actuator/prometheus") String metrics() { return "metrics"; }
    }
    @Test @WithMockUser void ordinaryAuthenticatedUserCannotReadPublicPortMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus").servletPath("/actuator/prometheus")
            .with(request -> { request.setLocalPort(8080); return request; })).andExpect(status().isForbidden());
    }
    @Test void privateListenerAcceptsScraper() throws Exception {
        mvc.perform(get("/actuator/prometheus").servletPath("/actuator/prometheus")
            .with(request -> { request.setLocalPort(9091); return request; })).andExpect(status().isOk());
    }
    @Test void nonAllowlistedActuatorEndpointIsDeniedEvenOnPrivatePort() throws Exception {
        mvc.perform(get("/actuator/env").servletPath("/actuator/env")
            .with(request -> { request.setLocalPort(9091); return request; })).andExpect(status().isForbidden());
    }
}
