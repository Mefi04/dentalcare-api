package com.dentalcare.api.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitInterceptorTests {

    @Test
    void appliesApiReadPolicyForGetRequests() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        ApiRateLimitInterceptor interceptor = new ApiRateLimitInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
        request.setServletPath("/api/v1/patients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(service).checkIp(RateLimitPolicy.API_READ, request);
        verify(service, never()).checkIdentity(any(), any());
    }

    @Test
    void skipsSensitiveEndpointsAlreadyHandledByFilter() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        ApiRateLimitInterceptor interceptor = new ApiRateLimitInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(service, never()).checkIp(any(), any());
    }
}
