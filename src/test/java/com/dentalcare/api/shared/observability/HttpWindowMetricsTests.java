package com.dentalcare.api.shared.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpWindowMetricsTests {
    @Test void firstScrapeSeesEntireBurstAndWindowExpiresWithoutMoreTraffic() {
        var clock = new AtomicLong(3600); // An established process, not just application startup.
        var window = new HttpWindowMetrics(clock::get);
        var registry = new SimpleMeterRegistry(); window.bindTo(registry);
        for (String status : java.util.List.of("401", "403", "429", "500", "503"))
            for (int i = 0; i < 40; i++) window.record(status, false);
        // No gauge was read before the burst. Sampling must not require a zero reference.
        assertEquals(40, registry.get("dentalcare.http.window.requests").tags("status", "401", "route", "application").gauge().value());
        assertEquals(80, registry.get("dentalcare.http.window.requests").tags("status", "5xx", "route", "application").gauge().value());
        clock.addAndGet(299);
        assertEquals(40, registry.get("dentalcare.http.window.requests").tags("status", "429", "route", "application").gauge().value());
        clock.incrementAndGet();
        assertTrue(registry.getMeters().stream().allMatch(meter -> ((io.micrometer.core.instrument.Gauge) meter).value() == 0));
    }
    @Test void noTrafficIsZeroAndRepeatedScrapesDoNotIncrementAnything() {
        var clock = new AtomicLong(0);
        var window = new HttpWindowMetrics(clock::get);
        var registry = new SimpleMeterRegistry(); window.bindTo(registry);
        assertEquals(10, registry.getMeters().size());
        assertTrue(registry.getMeters().stream().allMatch(meter -> ((io.micrometer.core.instrument.Gauge) meter).value() == 0));
        window.record("401", true);
        var management = registry.get("dentalcare.http.window.requests").tags("status", "401", "route", "management").gauge();
        assertEquals(1, management.value()); assertEquals(1, management.value());
        assertEquals(0, registry.get("dentalcare.http.window.requests").tags("status", "401", "route", "application").gauge().value());
    }
}
