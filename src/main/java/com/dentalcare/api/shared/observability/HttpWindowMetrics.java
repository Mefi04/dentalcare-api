package com.dentalcare.api.shared.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** A bounded rolling view, not another cumulative HTTP counter. No scrape baseline is needed. */
public final class HttpWindowMetrics implements MeterBinder, ObservationHandler<ServerRequestObservationContext> {
    private static final int SECONDS = 300;
    private static final String[] STATUSES = {"401", "403", "429", "5xx", "other"};
    private final LongSupplier seconds;
    private final long[] epochs = new long[SECONDS];
    private final long[][] counts = new long[SECONDS][10];
    private final DefaultServerRequestObservationConvention convention = new DefaultServerRequestObservationConvention();

    public HttpWindowMetrics() { this(() -> System.nanoTime() / 1_000_000_000L); }
    HttpWindowMetrics(LongSupplier seconds) { this.seconds = seconds; Arrays.fill(epochs, Long.MIN_VALUE); }
    @Override public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }
    @Override public void bindTo(MeterRegistry registry) {
        for (int route = 0; route < 2; route++) {
            for (int status = 0; status < STATUSES.length; status++) {
                int index = route * STATUSES.length + status;
                Gauge.builder("dentalcare.http.window.requests", this, window -> window.value(index))
                    .tag("status", STATUSES[status]).tag("route", route == 0 ? "application" : "management")
                    .description("Completed HTTP observations in the last 300 one-second buckets")
                    .register(registry);
            }
        }
    }
    @Override public void onStop(ServerRequestObservationContext context) {
        // Use Spring's status convention, the same source used by its existing HTTP timer.
        String status = convention.getLowCardinalityKeyValues(context).stream()
            .filter(value -> value.getKey().equals("status")).map(value -> value.getValue()).findFirst().orElse("other");
        boolean management = context.getCarrier().getServletPath().startsWith("/actuator");
        record(status, management);
    }
    synchronized void record(String status, boolean management) {
        int category = switch (status) {
            case "401" -> 0;
            case "403" -> 1;
            case "429" -> 2;
            default -> status.matches("5[0-9]{2}") ? 3 : 4;
        };
        long now = seconds.getAsLong();
        int bucket = Math.floorMod(now, SECONDS);
        if (epochs[bucket] != now) { epochs[bucket] = now; Arrays.fill(counts[bucket], 0); }
        counts[bucket][(management ? 5 : 0) + category]++;
    }
    private synchronized double value(int category) {
        long now = seconds.getAsLong();
        long sum = 0;
        for (int bucket = 0; bucket < SECONDS; bucket++) {
            if (epochs[bucket] != Long.MIN_VALUE && now - epochs[bucket] >= 0 && now - epochs[bucket] < SECONDS)
                sum += counts[bucket][category];
        }
        return sum;
    }
}
