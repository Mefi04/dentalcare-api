package com.dentalcare.api.modules.notifications.repository;

import com.dentalcare.api.modules.notifications.model.*;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PatientNotificationRepositoryIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PatientNotificationRepository notifications;
    @Autowired PatientNotificationPreferenceRepository preferences;
    @Autowired PatientRepository patients;
    @Autowired EntityManager entityManager;

    @Test
    void persistsOwnershipReadStateOrderingAndPreferences() {
        Patient owner = patient("PAC-N-001", "9100000000001");
        Patient other = patient("PAC-N-002", "9100000000002");
        PatientNotification older = notification(owner, NOW.minusSeconds(60));
        PatientNotification newer = notification(owner, NOW);
        PatientNotification foreign = notification(other, NOW.plusSeconds(60));
        notifications.saveAllAndFlush(List.of(older, newer, foreign));
        preferences.saveAndFlush(new PatientNotificationPreference(UUID.randomUUID(), owner,
                true, false, true, false, NOW, NOW));

        assertThat(notifications.findByPatient_Id(owner.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent())
                .extracting(PatientNotification::getId).containsExactly(newer.getId(), older.getId());
        assertThat(notifications.findByIdAndPatient_Id(foreign.getId(), owner.getId())).isEmpty();
        assertThat(notifications.markAllUnreadAsRead(owner.getId(), NOW.plusSeconds(120))).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();
        assertThat(notifications.findById(older.getId()).orElseThrow().getReadAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(notifications.findById(foreign.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(preferences.findByPatient_Id(owner.getId()).orElseThrow().isPaymentsEnabled()).isFalse();
    }

    private PatientNotification notification(Patient patient, Instant createdAt) {
        return new PatientNotification(UUID.randomUUID(), patient, NotificationEventType.APPOINTMENT_SCHEDULED,
                "Cita programada", "Tu cita fue programada.", null, createdAt);
    }

    private Patient patient(String code, String dpi) {
        Patient value = new Patient();
        value.setId(UUID.randomUUID());
        value.setCode(code);
        value.setName("Paciente");
        value.setDpi(dpi);
        value.setBirthDate(LocalDate.of(1990, 1, 1));
        value.setGender(Gender.OTHER);
        value.setPhone("5555-0000");
        value.setCreatedAt(NOW);
        value.setUpdatedAt(NOW);
        return patients.saveAndFlush(value);
    }
}
