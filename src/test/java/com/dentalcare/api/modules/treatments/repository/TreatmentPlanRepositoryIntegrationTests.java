package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class TreatmentPlanRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TreatmentPlanRepository treatmentPlanRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsRelationshipsListsByPatientAndOrdersPlansAndItems() {
        Patient owner = patient("PAC-TP-001", "8000000000001");
        Patient other = patient("PAC-TP-002", "8000000000002");
        User dentist = dentist("8000000000101");
        TreatmentPlan older = plan(owner, dentist, "Anterior", NOW.minusSeconds(60));
        older.replaceItems(List.of(item("Segundo", 1), item("Primero", 0)));
        older.approve(NOW.minusSeconds(30));
        TreatmentPlan newer = plan(owner, dentist, "Nuevo", NOW);
        newer.addItem(item("Único", 0));
        TreatmentPlan foreign = plan(other, dentist, "Otro", NOW.plusSeconds(60));
        foreign.addItem(item("Ajeno", 0));
        foreign.approve(NOW.plusSeconds(30));
        treatmentPlanRepository.saveAllAndFlush(List.of(older, newer, foreign));
        entityManager.clear();

        var page = treatmentPlanRepository.findByPatient_Id(owner.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        TreatmentPlan detailed = treatmentPlanRepository.findDetailedById(older.getId()).orElseThrow();

        assertThat(page.getContent()).extracting(TreatmentPlan::getName).containsExactly("Nuevo", "Anterior");
        assertThat(detailed.getPatient().getId()).isEqualTo(owner.getId());
        assertThat(detailed.getProfessional().getId()).isEqualTo(dentist.getId());
        assertThat(detailed.getItems()).extracting(TreatmentPlanItem::getName)
                .containsExactly("Primero", "Segundo");
        assertThat(treatmentPlanRepository.findDetailedByIdForUpdate(older.getId())).isPresent();
        var approvedIds = treatmentPlanRepository.findApprovedIdsByPatientId(owner.getId(),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        assertThat(approvedIds.getContent()).containsExactly(older.getId());
        assertThat(treatmentPlanRepository.findDetailedByIdIn(approvedIds.getContent()))
                .extracting(TreatmentPlan::getId).containsExactly(older.getId());
        assertThat(treatmentPlanRepository.findApprovedOwnedById(older.getId(), owner.getId())).isPresent();
        assertThat(treatmentPlanRepository.findApprovedOwnedById(newer.getId(), owner.getId())).isEmpty();
        assertThat(treatmentPlanRepository.findApprovedOwnedById(foreign.getId(), owner.getId())).isEmpty();
    }

    @Test
    void replacingItemsDeletesOrphans() {
        Patient patient = patient("PAC-TP-003", "8000000000003");
        User dentist = dentist("8000000000102");
        TreatmentPlan plan = plan(patient, dentist, "Plan", NOW);
        TreatmentPlanItem removed = item("Anterior", 0);
        plan.addItem(removed);
        treatmentPlanRepository.saveAndFlush(plan);

        treatmentPlanRepository.deleteItemsByTreatmentPlanId(plan.getId());
        plan = treatmentPlanRepository.findDetailedByIdForUpdate(plan.getId()).orElseThrow();
        plan.addItem(item("Nuevo", 0));
        treatmentPlanRepository.saveAndFlush(plan);
        entityManager.clear();

        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM treatment_plan_items WHERE id = :id")
                .setParameter("id", removed.getId()).getSingleResult();
        assertThat(count.intValue()).isZero();
        assertThat(treatmentPlanRepository.findDetailedById(plan.getId()).orElseThrow().getItems())
                .extracting(TreatmentPlanItem::getName).containsExactly("Nuevo");
    }

    @Test
    void databaseRejectsDuplicatePosition() {
        Patient patient = patient("PAC-TP-004", "8000000000004");
        User dentist = dentist("8000000000103");
        TreatmentPlan plan = plan(patient, dentist, "Plan", NOW);
        plan.replaceItems(List.of(item("Uno", 0), item("Dos", 0)));

        assertThatThrownBy(() -> treatmentPlanRepository.saveAndFlush(plan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidItemValues() {
        Patient patient = patient("PAC-TP-005", "8000000000005");
        User dentist = dentist("8000000000104");
        TreatmentPlan plan = plan(patient, dentist, "Plan", NOW);
        plan.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Item", null, 0,
                new BigDecimal("-1.00"), -1));

        assertThatThrownBy(() -> treatmentPlanRepository.saveAndFlush(plan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidStatusAndMissingForeignKeys() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO treatment_plans
                    (id, patient_id, professional_id, name, status, created_at, updated_at)
                VALUES (:id, :patient, :professional, 'Plan', 'UNKNOWN', NOW(), NOW())
                """).setParameter("id", UUID.randomUUID())
                .setParameter("patient", UUID.randomUUID())
                .setParameter("professional", UUID.randomUUID()).executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void seedsOnlyApprovedTreatmentPlanRoleAssignments() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'TREATMENT_PLAN_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "TREATMENT_PLAN_READ:ADMINISTRATOR", "TREATMENT_PLAN_READ:DENTIST",
                "TREATMENT_PLAN_READ:ASSISTANT", "TREATMENT_PLAN_CREATE:DENTIST",
                "TREATMENT_PLAN_CREATE:ASSISTANT", "TREATMENT_PLAN_UPDATE:DENTIST",
                "TREATMENT_PLAN_UPDATE:ASSISTANT", "TREATMENT_PLAN_APPROVE:DENTIST");
    }

    private TreatmentPlan plan(Patient patient, User dentist, String name, Instant createdAt) {
        return new TreatmentPlan(UUID.randomUUID(), patient, dentist, name, null,
                TreatmentPlanStatus.DRAFT, createdAt, createdAt);
    }

    private TreatmentPlanItem item(String name, int position) {
        return new TreatmentPlanItem(UUID.randomUUID(), name, null, 1,
                new BigDecimal("10.00"), position);
    }

    private Patient patient(String code, String dpi) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente");
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
        User user = new User(UUID.randomUUID(), "dentist-" + cui, "Dentista",
                "dentist-" + cui + "@example.test", cui, "hash", UserStatus.ACTIVE, NOW, NOW);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }
}
