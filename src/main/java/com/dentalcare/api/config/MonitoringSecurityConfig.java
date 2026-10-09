package com.dentalcare.api.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class MonitoringSecurityConfig {
    @Bean @Order(0)
    SecurityFilterChain monitoringSecurity(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${server.port:8080}") int apiPort) throws Exception {
        // Port 9091 is a private container-network listener and must never be published.
        http.securityMatcher(request -> request.getServletPath().startsWith("/actuator")
                || EndpointRequest.toAnyEndpoint().matches(request))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(request -> apiPort != 9091 && request.getLocalPort() == 9091
                    && (request.getServletPath().equals("/actuator/prometheus")
                        || request.getServletPath().equals("/actuator/health"))).permitAll()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
