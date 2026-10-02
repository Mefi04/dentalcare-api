package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalDocumentRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ClinicalDocumentRepository clinicalDocumentRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Persists clinical document and executes patient and type queries correctly")
    void persistsClinicalDocumentAndPerformsQueries() {
        Patient patient1 = patient("PAC-DOC-001", "8000000000301");
        Patient patient2 = patient("PAC-DOC-002", "8000000000302");
        User dentist = dentist("8000000000303");

        ClinicalDocument doc1 = new ClinicalDocument(
                UUID.randomUUID(),
                patient1,
                dentist,
                "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY,
                "Control inicial",
                LocalDate.of(2026, 10, 1),
                NOW,
                NOW
        );
        clinicalDocumentRepository.saveAndFlush(doc1);

        ClinicalDocument doc2 = new ClinicalDocument(
                UUID.randomUUID(),
                patient1,
                dentist,
                "Hemograma Completo",
                ClinicalDocumentType.LAB_RESULT,
                "Valores dentro de rango normal",
                LocalDate.of(2026, 10, 1),
                NOW,
                NOW
        );
        clinicalDocumentRepository.saveAndFlush(doc2);

        ClinicalDocument docPatient2 = new ClinicalDocument(
                UUID.randomUUID(),
                patient2,
                dentist,
                "Consentimiento Informado Endodoncia",
                ClinicalDocumentType.INFORMED_CONSENT,
                null,
                LocalDate.of(2026, 10, 1),
                NOW,
                NOW
        );
        clinicalDocumentRepository.saveAndFlush(docPatient2);
        entityManager.clear();

        // Query by patient 1
        var pageP1 = clinicalDocumentRepository.findByPatient_Id(patient1.getId(), PageRequest.of(0, 10));
        assertThat(pageP1.getContent()).hasSize(2);

        // Query by patient 1 and type
        var pageP1Type = clinicalDocumentRepository.findByPatient_IdAndType(
                patient1.getId(), ClinicalDocumentType.RADIOGRAPHY, PageRequest.of(0, 10));
        assertThat(pageP1Type.getContent()).hasSize(1);
        assertThat(pageP1Type.getContent().getFirst().getTitle()).isEqualTo("Radiografía Panorámica");

        // Query by id and correct patient
        Optional<ClinicalDocument> found = clinicalDocumentRepository.findByIdAndPatient_Id(doc1.getId(), patient1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAuthor().getFullName()).isEqualTo("Dr. Clinico Test");

        // Query by id and WRONG patient (Cross-patient IDOR protection)
        Optional<ClinicalDocument> notFoundWrongPatient = clinicalDocumentRepository.findByIdAndPatient_Id(doc1.getId(), patient2.getId());
        assertThat(notFoundWrongPatient).isEmpty();
    }

    @Test
    @DisplayName("Database check constraint rejects empty title")
    void databaseRejectsEmptyTitleCheckConstraint() {
        Patient patient = patient("PAC-DOC-003", "8000000000304");
        User dentist = dentist("8000000000305");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO clinical_documents
                    (id, patient_id, author_id, title, type, document_date, created_at, updated_at)
                VALUES (:id, :patient, :author, '   ', 'RADIOGRAPHY', CURRENT_DATE, NOW(), NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("author", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Database check constraint rejects invalid document type")
    void databaseRejectsInvalidTypeCheckConstraint() {
        Patient patient = patient("PAC-DOC-004", "8000000000306");
        User dentist = dentist("8000000000307");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO clinical_documents
                    (id, patient_id, author_id, title, type, document_date, created_at, updated_at)
                VALUES (:id, :patient, :author, 'Titulo Valido', 'INVALID_TYPE', CURRENT_DATE, NOW(), NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("author", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    private Patient patient(String code, String dpi) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente Test");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-0000");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patientRepository.saveAndFlush(patient);
    }

    private User dentist(String cui) {
        Role role = roleRepository.findByCode("DENTIST").orElseThrow();
        User user = new User(UUID.randomUUID(), "dentist-" + cui, "Dr. Clinico Test",
                "dentist-" + cui + "@example.test", cui, "hash", UserStatus.ACTIVE, NOW, NOW);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }
}
