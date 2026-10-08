package com.dentalcare.api.security.ratelimit;

import com.dentalcare.api.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public ApiRateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        String pageParam = request.getParameter("page");
        if (pageParam != null) {
            try {
                if (Integer.parseInt(pageParam) > 1000) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Page offset too large");
                    return false;
                }
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page parameter");
                return false;
            }
        }

        // Skip explicitly matched unauthenticated sensitive endpoints (handled by filter)
        if (path.equals("/api/v1/auth/login") ||
            path.equals("/api/v1/auth/mobile/login") ||
            path.equals("/api/v1/auth/password-recovery/request") ||
            path.equals("/api/v1/public/contact-inquiries")) {
            return true;
        }

        if (!path.startsWith("/api/")) {
            return true;
        }

        RateLimitPolicy policy = determinePolicy(request.getMethod(), path);
        if (policy == null) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            rateLimitService.checkIdentity(policy, user.userId().toString());
        } else {
            rateLimitService.checkIp(policy, request);
        }

        return true;
    }

    private RateLimitPolicy determinePolicy(String method, String path) {
        if (path.startsWith("/api/v1/reports")) {
            return RateLimitPolicy.REPORTS;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return RateLimitPolicy.API_READ;
        }
        return RateLimitPolicy.API_WRITE;
    }
}
