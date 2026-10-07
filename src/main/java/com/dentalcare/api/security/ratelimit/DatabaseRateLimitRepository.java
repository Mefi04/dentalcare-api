package com.dentalcare.api.security.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseRateLimitRepository {
    private static final String CONSUME_SQL = """
            INSERT INTO security_rate_limits (bucket_key, window_started_at, request_count, expires_at, updated_at)
            VALUES (?, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'), CURRENT_TIMESTAMP)
            ON CONFLICT (bucket_key) DO UPDATE SET
                window_started_at = CASE WHEN security_rate_limits.expires_at <= CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP ELSE security_rate_limits.window_started_at END,
                request_count = CASE WHEN security_rate_limits.expires_at <= CURRENT_TIMESTAMP THEN 1 ELSE security_rate_limits.request_count + 1 END,
                expires_at = CASE WHEN security_rate_limits.expires_at <= CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond') ELSE security_rate_limits.expires_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE security_rate_limits.expires_at <= CURRENT_TIMESTAMP OR security_rate_limits.request_count < ?
            RETURNING expires_at
            """;

    private final JdbcTemplate jdbc;
    private final AtomicLong operations = new AtomicLong();

    public DatabaseRateLimitRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RateLimitDecision consume(String bucketKey, int limit, Duration window) {
        long millis = window.toMillis();
        List<Boolean> accepted = jdbc.query(CONSUME_SQL,
                (resultSet, row) -> Boolean.TRUE,
                bucketKey, millis, millis, limit);
        cleanupOccasionally();
        if (!accepted.isEmpty()) return RateLimitDecision.permit();
        Long retryAfter = jdbc.queryForObject("""
                SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM (expires_at - CURRENT_TIMESTAMP))))::bigint
                FROM security_rate_limits WHERE bucket_key = ?
                """, Long.class, bucketKey);
        return RateLimitDecision.blocked(retryAfter == null ? 1 : retryAfter);
    }

    private void cleanupOccasionally() {
        if (operations.incrementAndGet() % 1_000 == 0) {
            jdbc.update("DELETE FROM security_rate_limits WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
        }
    }
}
