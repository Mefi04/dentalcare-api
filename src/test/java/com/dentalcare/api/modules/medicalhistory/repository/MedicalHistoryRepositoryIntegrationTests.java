package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MedicalHistoryRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsStructuredHistoryAndReloadsItByPatient() {
        Patient patient = createPatient("PAC-MH-001", "9000000000201");
        MedicalHistory history = new MedicalHistory(UUID.randomUUID(), patient, NOW, NOW);
        history.replaceAllergies(List.of("Penicilina", "Látex"));
        history.replaceCurrentMedications(List.of("Metformina 500 mg"));
        history.replaceRelevantConditions(List.of("Diabetes tipo 2"));
        history.setObservations("Antecedentes confirmados con el paciente");
        medicalHistoryRepository.saveAndFlush(history);
        entityManager.clear();

        MedicalHistory reloaded = medicalHistoryRepository.findByPatient_Id(patient.getId()).orElseThrow();

        assertThat(reloaded.getPatient().getId()).isEqualTo(patient.getId());
        assertThat(reloaded.getAllergies()).containsExactlyInAnyOrder("Penicilina", "Látex");
        assertThat(reloaded.getCurrentMedications()).containsExactly("Metformina 500 mg");
        assertThat(reloaded.getRelevantConditions()).containsExactly("Diabetes tipo 2");
        assertThat(reloaded.getObservations()).isEqualTo("Antecedentes confirmados con el paciente");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void enforcesOneMedicalHistoryPerPatient() {
        Patient patient = createPatient("PAC-MH-002", "9000000000202");
        medicalHistoryRepository.saveAndFlush(new MedicalHistory(UUID.randomUUID(), patient, NOW, NOW));
        entityManager.clear();

        assertThatThrownBy(() -> medicalHistoryRepository.saveAndFlush(
                new MedicalHistory(UUID.randomUUID(), patient, NOW, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Patient createPatient(String code, String dpi) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente Historia");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1988, 4, 12));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-2020");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patientRepository.saveAndFlush(patient);
    }
}
