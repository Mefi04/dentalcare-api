package com.dentalcare.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(DatabaseRateLimitRepository.class)
class DatabaseRateLimitRepositoryIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
    }

    @Autowired DatabaseRateLimitRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sharedAtomicCounterBlocksAtLimitAndRecoversAfterWindow() {
        assertThat(repository.consume("LOGIN:ip:a", 2, Duration.ofMinutes(5)).allowed()).isTrue();
        assertThat(repository.consume("LOGIN:ip:a", 2, Duration.ofMinutes(5)).allowed()).isTrue();
        RateLimitDecision blocked = repository.consume("LOGIN:ip:a", 2, Duration.ofMinutes(5));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isPositive();

        jdbc.update("UPDATE security_rate_limits SET window_started_at = CURRENT_TIMESTAMP - INTERVAL '2 seconds', "
                + "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE bucket_key = ?", "LOGIN:ip:a");
        assertThat(repository.consume("LOGIN:ip:a", 2, Duration.ofMinutes(5)).allowed()).isTrue();
    }

    @Test
    void differentBucketsDoNotBlockEachOtherAndMigrationAddsIndex() {
        assertThat(repository.consume("LOGIN:identity:a", 1, Duration.ofMinutes(5)).allowed()).isTrue();
        assertThat(repository.consume("LOGIN:identity:a", 1, Duration.ofMinutes(5)).allowed()).isFalse();
        assertThat(repository.consume("LOGIN:identity:b", 1, Duration.ofMinutes(5)).allowed()).isTrue();
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename='security_rate_limits'", String.class))
                .contains("security_rate_limits_pkey", "idx_security_rate_limits_expires_at");
    }
}
