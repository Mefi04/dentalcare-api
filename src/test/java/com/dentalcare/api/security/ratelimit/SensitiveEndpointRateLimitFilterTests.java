package com.dentalcare.api.security.ratelimit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

class SensitiveEndpointRateLimitFilterTests {
    @Test
    void protectsOnlyExactSensitivePostRoutes() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        SensitiveEndpointRateLimitFilter filter = new SensitiveEndpointRateLimitFilter(service, resolver);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);
        verify(service).checkIp(RateLimitPolicy.LOGIN, request);
        verify(chain).doFilter(request, response);
    }

    @Test
    void delegatesBlockedRequestToGlobalExceptionResolverWithoutCallingEndpoint() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        SensitiveEndpointRateLimitFilter filter = new SensitiveEndpointRateLimitFilter(service, resolver);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/public/contact-inquiries");
        request.setServletPath("/api/v1/public/contact-inquiries");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.mockito.Mockito.doThrow(new RateLimitExceededException(60)).when(service)
                .checkIp(RateLimitPolicy.PUBLIC_CONTACT, request);

        filter.doFilter(request, response, chain);
        verify(resolver).resolveException(org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isA(RateLimitExceededException.class));
        verify(chain, never()).doFilter(request, response);
    }
}
