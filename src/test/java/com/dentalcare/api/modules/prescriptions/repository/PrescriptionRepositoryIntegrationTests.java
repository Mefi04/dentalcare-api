package com.dentalcare.api.modules.prescriptions.repository;

import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.prescriptions.model.Prescription;
import com.dentalcare.api.modules.prescriptions.model.PrescriptionStatus;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PrescriptionRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PrescriptionRepository prescriptions;
    @Autowired private PatientRepository patients;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsListsAndEnforcesPatientOwnershipQuery() {
        Patient owner = patient("PAC-RX-001", "8100000000001");
        Patient other = patient("PAC-RX-002", "8100000000002");
        User dentist = dentist("8100000000101");
        Prescription older = prescription(owner, dentist, "Ibuprofeno", NOW.minusSeconds(60));
        Prescription newer = prescription(owner, dentist, "Amoxicilina", NOW);
        Prescription foreign = prescription(other, dentist, "Paracetamol", NOW.plusSeconds(60));
        prescriptions.saveAllAndFlush(List.of(older, newer, foreign));
        entityManager.clear();

        var page = prescriptions.findByPatient_Id(owner.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id"))));

        assertThat(page.getContent()).extracting(Prescription::getMedication)
                .containsExactly("Amoxicilina", "Ibuprofeno");
        assertThat(prescriptions.findById(newer.getId())).isPresent();
        assertThat(prescriptions.findByIdAndPatient_Id(newer.getId(), owner.getId())).isPresent();
        assertThat(prescriptions.findByIdAndPatient_Id(foreign.getId(), owner.getId())).isEmpty();
    }

    @Test
    void migrationSeedsExpectedPrescriptionPermissions() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'PRESCRIPTION_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "PRESCRIPTION_READ:ADMINISTRATOR", "PRESCRIPTION_READ:DENTIST",
                "PRESCRIPTION_READ:ASSISTANT", "PRESCRIPTION_CREATE:DENTIST");
    }

    private Prescription prescription(Patient patient, User dentist, String medication, Instant issuedAt) {
        return new Prescription(UUID.randomUUID(), patient, dentist, medication, "Tableta 500 mg",
                "500 mg", "Cada 8 horas", "7 días", null, issuedAt, PrescriptionStatus.ISSUED);
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

    private User dentist(String cui) {
        Role role = roles.findByCode("DENTIST").orElseThrow();
        User value = new User(UUID.randomUUID(), "dentist-" + cui, "Dentista",
                "dentist-" + cui + "@example.test", cui, "hash", UserStatus.ACTIVE, NOW, NOW);
        value.setRoles(Set.of(role));
        return users.saveAndFlush(value);
    }
}
