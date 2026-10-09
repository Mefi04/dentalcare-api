package com.dentalcare.api.config;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringMetricsTests {
    @Test void initializesBoundedErrorSeriesAtZeroSoFirstFailuresCanAlert() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        registry.config().meterFilter(new MonitoringMetricsConfig().safeHttpTags());
        new MonitoringMetricsConfig().operationBaselines().customize(registry);
        assertEquals(20, registry.getMeters().size());
        assertEquals(0, registry.get("dentalcare.audit.failures").counter().count());
        assertEquals(0, registry.get("dentalcare.storage.operations").tag("operation", "load").tag("result", "failure").timer().count());
        assertEquals(0, registry.get("dentalcare.database.errors").tag("kind", "connection").counter().count());
    }
    @Test void stripsAllUnboundedTagsAndNormalizesEarlyRejections() {
        var id = new Meter.Id("http.server.requests", Tags.of("uri", "/api/v1/patients/PRIVATE_PATIENT/documents/PRIVATE_DOCUMENT?token=PRIVATE_TOKEN", "method", "PRIVATE_METHOD", "status", "401", "exception", "PRIVATE_EXCEPTION", "user", "PRIVATE_USER"), null, null, Meter.Type.TIMER);
        var mapped = new MonitoringMetricsConfig().safeHttpTags().map(id);
        assertEquals("documents", mapped.getTag("uri"));
        assertEquals("OTHER", mapped.getTag("method"));
        assertEquals("401", mapped.getTag("status"));
        assertEquals(3, mapped.getTags().size());
        assertFalse(mapped.toString().contains("PRIVATE_"));
    }
}
