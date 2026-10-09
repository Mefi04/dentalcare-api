package com.dentalcare.api.shared.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** An allowlist, not best-effort regex redaction. Messages, arguments and throwable text never leave the process. */
public class SafeJsonEncoder extends EncoderBase<ILoggingEvent> {
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public byte[] headerBytes() { return null; }
    @Override public byte[] footerBytes() { return null; }
    @Override public byte[] encode(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", java.time.Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("level", event.getLevel().toString());
        fields.put("logger", event.getLoggerName());
        fields.put("event", "operational_log");
        if (event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if (pair.key.equals("event") && pair.value instanceof String code && java.util.Set.of("http_request", "database_failure", "storage_failure", "audit_failure").contains(code)) fields.put("event", code);
                if (pair.key.equals("status") && pair.value instanceof Integer status && status >= 100 && status <= 599) fields.put("status", status);
                if (pair.key.equals("duration_ms") && pair.value instanceof Long duration && duration >= 0) fields.put("duration_ms", duration);
            }
        }
        String id = event.getMDCPropertyMap().get("request_id");
        if (id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) fields.put("request_id", id);
        // Throwable class is useful diagnostically; its message, causes and stack are intentionally excluded.
        if (event.getThrowableProxy() != null) fields.put("exception_type", event.getThrowableProxy().getClassName());
        try { return (mapper.writeValueAsString(fields) + "\n").getBytes(StandardCharsets.UTF_8); }
        catch (Exception ignored) { return "{\"event\":\"log_encoding_failure\"}\n".getBytes(StandardCharsets.UTF_8); }
    }
}
