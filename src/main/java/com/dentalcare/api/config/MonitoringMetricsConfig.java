package com.dentalcare.api.config;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class MonitoringMetricsConfig {
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<com.dentalcare.api.shared.observability.CorrelationIdFilter>
            correlationRegistration(com.dentalcare.api.shared.observability.CorrelationIdFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST, jakarta.servlet.DispatcherType.ERROR);
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
    @Bean com.dentalcare.api.shared.observability.HttpWindowMetrics httpWindowMetrics() {
        return new com.dentalcare.api.shared.observability.HttpWindowMetrics();
    }
    @Bean org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer<MeterRegistry> operationBaselines() {
        return registry -> {
            for (String completion : java.util.List.of("rolled_back", "unknown")) {
                registry.counter("dentalcare.transaction.commit.failures", "completion", completion);
            }
            for (String kind : java.util.List.of("constraint", "timeout", "connection", "other")) {
                registry.counter("dentalcare.database.errors", "kind", kind);
            }
            registry.counter("dentalcare.audit.failures", "kind", "persistence");
            for (String operation : java.util.List.of("store", "load", "getMetadata", "exists", "delete")) {
                for (String result : java.util.List.of("success", "failure")) {
                    registry.timer("dentalcare.storage.operations", "operation", operation, "result", result);
                }
            }
            for (String result : java.util.List.of("success", "failure", "cancelled")) {
                registry.counter("dentalcare.storage.transfers", "result", result);
            }
        };
    }
    @Bean MeterFilter safeHttpTags() {
        return new MeterFilter() {
            @Override public Meter.Id map(Meter.Id id) {
                if (!id.getName().startsWith("http.server.requests")) return id;
                // No raw routes, exception names or arbitrary methods, including early security rejections.
                String uri = id.getTag("uri");
                String group = uri == null ? "other" : uri.contains("/documents") ? "documents"
                    : uri.startsWith("/api/v1/auth/") ? "auth" : uri.startsWith("/api/") ? "api"
                    : uri.startsWith("/actuator") ? "management" : "other";
                String method = id.getTag("method");
                if (!java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method == null ? "" : method)) method = "OTHER";
                String status = id.getTag("status");
                if (status == null || !status.matches("[1-5][0-9]{2}")) status = "UNKNOWN";
                return id.replaceTags(java.util.List.of(Tag.of("uri", group), Tag.of("method", method), Tag.of("status", status)));
            }
        };
    }
}
