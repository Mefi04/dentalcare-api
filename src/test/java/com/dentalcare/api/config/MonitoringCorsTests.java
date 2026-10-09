package com.dentalcare.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringCorsTests {
    @Test void authorizedFrontendCanReadGeneratedRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/audit-events");
        request.addHeader("Origin", "http://localhost:3000");
        var response = new MockHttpServletResponse();
        var filter = new CorsFilter(new CorsConfig().corsConfigurationSource("http://localhost:3000"));
        filter.doFilter(request, response, (req, res) -> response.setHeader("X-Request-ID", "synthetic-id"));
        assertEquals("http://localhost:3000", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("X-Request-ID", response.getHeader("Access-Control-Expose-Headers"));
    }
    @Test void untrustedOriginRemainsRejected() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/audit-events");
        request.addHeader("Origin", "https://untrusted.invalid");
        var response = new MockHttpServletResponse();
        var filter = new CorsFilter(new CorsConfig().corsConfigurationSource("http://localhost:3000"));
        filter.doFilter(request, response, (req, res) -> fail("Untrusted origin reached application"));
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Expose-Headers"));
    }
}
