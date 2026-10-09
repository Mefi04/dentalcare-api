package com.dentalcare.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MonitoringSecurityTests.Probe.class, properties = "server.port=9091")
@Import({MonitoringSecurityConfig.class, MonitoringSecurityTests.Probe.class})
class MonitoringPortCollisionTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.dentalcare.api.security.jwt.JwtService jwtService;
    @Autowired MockMvc mvc;
    @Test @WithMockUser void cannotExposeMetricsByReusingManagementPortForApi() throws Exception {
        mvc.perform(get("/actuator/prometheus").servletPath("/actuator/prometheus")
            .with(request -> { request.setLocalPort(9091); return request; })).andExpect(status().isForbidden());
    }
}
