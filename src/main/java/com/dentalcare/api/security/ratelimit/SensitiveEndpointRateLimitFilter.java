package com.dentalcare.api.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@ConditionalOnBean(RateLimitService.class)
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final Map<String, RateLimitPolicy> POLICIES = Map.of(
            "/api/v1/auth/login", RateLimitPolicy.LOGIN,
            "/api/v1/auth/mobile/login", RateLimitPolicy.LOGIN,
            "/api/v1/auth/password-recovery/request", RateLimitPolicy.PASSWORD_RECOVERY,
            "/api/v1/public/contact-inquiries", RateLimitPolicy.PUBLIC_CONTACT,
            "/api/v1/public/appointment-requests", RateLimitPolicy.PUBLIC_APPOINTMENT_REQUEST,
            "/api/v1/public/assistant/messages", RateLimitPolicy.PUBLIC_ASSISTANT
    );
    private final RateLimitService service;
    private final HandlerExceptionResolver exceptionResolver;

    public SensitiveEndpointRateLimitFilter(RateLimitService service,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.service = service;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !POLICIES.containsKey(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            service.checkIp(POLICIES.get(request.getServletPath()), request);
            chain.doFilter(request, response);
        } catch (RateLimitExceededException exception) {
            exceptionResolver.resolveException(request, response, null, exception);
        }
    }
}
