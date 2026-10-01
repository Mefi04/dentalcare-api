package com.dentalcare.api.modules.auth.repository;

import com.dentalcare.api.modules.auth.model.PasswordRecoveryToken;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PasswordRecoveryTokenRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PasswordRecoveryTokenRepository tokens;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    void migrationPersistsOnlyHashAndFindsLatestTokenForUser() {
        User user = users.saveAndFlush(new User(UUID.randomUUID(), "recovery-patient", "Paciente",
                null, "9200000000001", "password-hash", UserStatus.ACTIVE, NOW, NOW));
        PasswordRecoveryToken older = new PasswordRecoveryToken(UUID.randomUUID(), user,
                "$2a$hash-only-older", NOW.minusSeconds(60), NOW.plusSeconds(300));
        PasswordRecoveryToken latest = new PasswordRecoveryToken(UUID.randomUUID(), user,
                "$2a$hash-only-latest", NOW, NOW.plusSeconds(600));
        tokens.saveAndFlush(older);
        tokens.saveAndFlush(latest);
        entityManager.clear();

        PasswordRecoveryToken found = tokens.findFirstByUser_IdOrderByRequestedAtDesc(user.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(latest.getId());
        assertThat(found.getCodeHash()).isEqualTo("$2a$hash-only-latest");
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getFailedAttempts()).isZero();
    }
}
