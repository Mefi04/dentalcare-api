package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalPreparation;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalRecordRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ClinicalAttentionRepository clinicalAttentionRepository;
    @Autowired private ClinicalDiagnosisRepository clinicalDiagnosisRepository;
    @Autowired private ClinicalEvolutionNoteRepository clinicalEvolutionNoteRepository;
    @Autowired private ClinicalPreparationRepository clinicalPreparationRepository;
    @Autowired private OdontogramFindingRepository odontogramFindingRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsClinicalEntitiesAndPerformsQueries() {
        Patient patient = patient("PAC-CR-001", "8000000000201");
        User dentist = dentist("8000000000202");

        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, dentist, null,
                "Evaluacion general", "Notas clinicas completas", "Planificar profilaxis",
                NOW, NOW, NOW
        );
        clinicalAttentionRepository.saveAndFlush(attention);

        ClinicalDiagnosis diagnosis = new ClinicalDiagnosis(
                UUID.randomUUID(), patient, attention, null, dentist,
                DiagnosisType.PRIMARY, "Caries oclusal en pieza 16", NOW
        );
        clinicalDiagnosisRepository.saveAndFlush(diagnosis);

        ClinicalEvolutionNote evolution = new ClinicalEvolutionNote(
                UUID.randomUUID(), patient, attention, dentist,
                LocalDate.of(2026, 10, 1), "Profilaxis", "Paciente refiere buena higiene", NOW
        );
        clinicalEvolutionNoteRepository.saveAndFlush(evolution);

        OdontogramFinding finding = new OdontogramFinding(
                UUID.randomUUID(), patient, attention, dentist,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries visible", NOW
        );
        odontogramFindingRepository.saveAndFlush(finding);
        entityManager.clear();

        var attentionPage = clinicalAttentionRepository.findByPatient_Id(patient.getId(), PageRequest.of(0, 10));
        assertThat(attentionPage.getContent()).hasSize(1);
        assertThat(attentionPage.getContent().getFirst().getReason()).isEqualTo("Evaluacion general");

        var diagnosisPage = clinicalDiagnosisRepository.findByPatient_Id(patient.getId(), PageRequest.of(0, 10));
        assertThat(diagnosisPage.getContent()).hasSize(1);
        assertThat(diagnosisPage.getContent().getFirst().getType()).isEqualTo(DiagnosisType.PRIMARY);

        var evolutionPage = clinicalEvolutionNoteRepository.findByPatient_Id(patient.getId(), PageRequest.of(0, 10));
        assertThat(evolutionPage.getContent()).hasSize(1);
        assertThat(evolutionPage.getContent().getFirst().getProcedureSummary()).isEqualTo("Profilaxis");

        var findingList = odontogramFindingRepository.findByPatient_IdAndDentitionOrderByCreatedAtAsc(
                patient.getId(), DentitionType.ADULT);
        assertThat(findingList).hasSize(1);
        assertThat(findingList.getFirst().getToothCode()).isEqualTo("16");
    }

    @Test
    void databaseRejectsEmptyNotesCheckConstraint() {
        Patient patient = patient("PAC-CR-002", "8000000000203");
        User dentist = dentist("8000000000204");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO clinical_attentions
                    (id, patient_id, professional_id, reason, clinical_notes, occurred_at, created_at, updated_at)
                VALUES (:id, :patient, :professional, 'Motivo', '   ', NOW(), NOW(), NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("professional", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void databaseRejectsInvalidFindingCheckConstraint() {
        Patient patient = patient("PAC-CR-003", "8000000000205");
        User dentist = dentist("8000000000206");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO odontogram_findings
                    (id, patient_id, author_id, dentition, tooth_code, finding, created_at)
                VALUES (:id, :patient, :author, 'ADULT', '16', 'INVALID_FINDING', NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("author", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void databaseRejectsInvalidSurfaceCheckConstraint() {
        Patient patient = patient("PAC-CR-003B", "8000000000215");
        User dentist = dentist("8000000000216");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO odontogram_findings
                    (id, patient_id, author_id, dentition, tooth_code, surface, finding, created_at)
                VALUES (:id, :patient, :author, 'ADULT', '16', 'INVALID_SURFACE', 'CARIOUS', NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("author", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void persistsEvolvedOdontogramFindingsAndQueriesChronologically() {
        Patient patient = patient("PAC-CR-003C", "8000000000217");
        User dentist = dentist("8000000000218");

        OdontogramFinding f1 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, dentist,
                DentitionType.ADULT, "11", ToothSurface.INCISAL,
                ToothFinding.CARIOUS, "Caries en borde incisal", NOW.minusSeconds(200)
        );
        OdontogramFinding f2 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, dentist,
                DentitionType.ADULT, "11", ToothSurface.INCISAL,
                ToothFinding.RESTORED, "Restaurado con resina estetica", NOW.minusSeconds(100)
        );
        OdontogramFinding f3 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, dentist,
                DentitionType.ADULT, "36", null,
                ToothFinding.IMPLANT, "Implante de titanio", NOW.minusSeconds(50)
        );
        OdontogramFinding f4 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, dentist,
                DentitionType.ADULT, "46", ToothSurface.LINGUAL,
                ToothFinding.FRACTURE, "Fractura cuspidea lingual", NOW
        );

        odontogramFindingRepository.saveAndFlush(f1);
        odontogramFindingRepository.saveAndFlush(f2);
        odontogramFindingRepository.saveAndFlush(f3);
        odontogramFindingRepository.saveAndFlush(f4);
        entityManager.clear();

        List<OdontogramFinding> chronological = odontogramFindingRepository
                .findByPatient_IdAndDentitionOrderByCreatedAtAscIdAsc(patient.getId(), DentitionType.ADULT);
        assertThat(chronological).hasSize(4);
        assertThat(chronological.get(0).getToothCode()).isEqualTo("11");
        assertThat(chronological.get(0).getFinding()).isEqualTo(ToothFinding.CARIOUS);
        assertThat(chronological.get(1).getToothCode()).isEqualTo("11");
        assertThat(chronological.get(1).getFinding()).isEqualTo(ToothFinding.RESTORED);
        assertThat(chronological.get(2).getToothCode()).isEqualTo("36");
        assertThat(chronological.get(2).getFinding()).isEqualTo(ToothFinding.IMPLANT);
        assertThat(chronological.get(3).getToothCode()).isEqualTo("46");
        assertThat(chronological.get(3).getFinding()).isEqualTo(ToothFinding.FRACTURE);
        assertThat(chronological.get(3).getSurface()).isEqualTo(ToothSurface.LINGUAL);

        var filterByTooth = odontogramFindingRepository.findByPatientWithFilters(
                patient.getId(), "11", DentitionType.ADULT, null, PageRequest.of(0, 10));
        assertThat(filterByTooth.getContent()).hasSize(2);

        var filterByFinding = odontogramFindingRepository.findByPatientWithFilters(
                patient.getId(), null, DentitionType.ADULT, ToothFinding.IMPLANT, PageRequest.of(0, 10));
        assertThat(filterByFinding.getContent()).hasSize(1);
        assertThat(filterByFinding.getContent().getFirst().getToothCode()).isEqualTo("36");
    }

    @Test
    void persistsClinicalPreparationAndPerformsQueries() {
        Patient patient = patient("PAC-CR-004", "8000000000207");
        User dentist = dentist("8000000000208");

        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, dentist, null,
                "Atencion con preparacion", "Notas clinicas", "Continuar monitoreo",
                NOW, NOW, NOW
        );
        clinicalAttentionRepository.saveAndFlush(attention);

        ClinicalPreparation prep = new ClinicalPreparation(
                UUID.randomUUID(), patient, attention, dentist,
                "120/80", 75, new BigDecimal("36.6"), new BigDecimal("68.5"),
                "Sin novedades en signos vitales", NOW, NOW
        );
        clinicalPreparationRepository.saveAndFlush(prep);
        entityManager.clear();

        var prepList = clinicalPreparationRepository.findByPatient_IdOrderByCreatedAtDesc(patient.getId());
        assertThat(prepList).hasSize(1);
        assertThat(prepList.getFirst().getBloodPressure()).isEqualTo("120/80");
        assertThat(prepList.getFirst().getHeartRate()).isEqualTo(75);

        var prepPage = clinicalPreparationRepository.findByPatient_Id(patient.getId(), PageRequest.of(0, 10));
        assertThat(prepPage.getContent()).hasSize(1);

        var latest = clinicalPreparationRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patient.getId());
        assertThat(latest).isPresent();
        assertThat(latest.get().getHeartRate()).isEqualTo(75);

        var byAttention = clinicalPreparationRepository.findFirstByAttention_IdOrderByCreatedAtDesc(attention.getId());
        assertThat(byAttention).isPresent();
        assertThat(byAttention.get().getId()).isEqualTo(prep.getId());

        var byIdAndPatient = clinicalPreparationRepository.findByIdAndPatient_Id(prep.getId(), patient.getId());
        assertThat(byIdAndPatient).isPresent();
    }

    @Test
    void databaseRejectsInvalidHeartRateCheckConstraint() {
        Patient patient = patient("PAC-CR-005", "8000000000209");
        User dentist = dentist("8000000000210");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO clinical_preparations
                    (id, patient_id, prepared_by_id, heart_rate, created_at, updated_at)
                VALUES (:id, :patient, :preparedBy, -10, NOW(), NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", patient.getId())
                .setParameter("preparedBy", dentist.getId()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void seedsClinicalRecordRoleAssignments() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'CLINICAL_RECORD_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "CLINICAL_RECORD_READ:ADMINISTRATOR",
                "CLINICAL_RECORD_READ:DENTIST",
                "CLINICAL_RECORD_READ:ASSISTANT",
                "CLINICAL_RECORD_WRITE:DENTIST",
                "CLINICAL_RECORD_WRITE:ASSISTANT"
        );
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
