package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
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
class TreatmentProcedureRepositoryIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-01T15:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TreatmentProcedureRepository procedures;
    @Autowired private TreatmentPlanRepository plans;
    @Autowired private PatientRepository patients;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsTraceabilityAndQueriesPlanAndPatientHistory() {
        Fixture fixture = fixture("PAC-PROC-001", "8100000000001", "8100000000101");
        TreatmentProcedure older = procedure(fixture, 1, NOW.minusSeconds(60));
        older.complete("Finalizado", NOW.minusSeconds(30));
        TreatmentProcedure newer = procedure(fixture, 2, NOW);
        procedures.saveAllAndFlush(List.of(older, newer));
        entityManager.clear();

        var pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("performedAt"), Sort.Order.desc("id")));
        var byPlan = procedures.findByTreatmentPlan_Id(fixture.plan().getId(), pageable);
        var byPatient = procedures.findByPatient_Id(fixture.patient().getId(), pageable);
        TreatmentProcedure detail = procedures.findDetailedById(newer.getId()).orElseThrow();

        assertThat(byPlan.getContent()).extracting(TreatmentProcedure::getSequenceNumber)
                .containsExactly(2, 1);
        assertThat(byPatient.getTotalElements()).isEqualTo(2);
        assertThat(detail.getTreatmentPlan().getId()).isEqualTo(fixture.plan().getId());
        assertThat(detail.getTreatmentPlanItem().getId()).isEqualTo(fixture.item().getId());
        assertThat(detail.getPatient().getId()).isEqualTo(fixture.patient().getId());
        assertThat(detail.getProfessional().getId()).isEqualTo(fixture.dentist().getId());
        assertThat(procedures.countByTreatmentPlanItem_IdAndStatus(
                fixture.item().getId(), TreatmentProcedureStatus.COMPLETED)).isEqualTo(1);
        assertThat(procedures.findDetailedByIdForUpdate(newer.getId())).isPresent();
    }

    @Test
    void databaseRejectsTwoActiveExecutionsForSamePlanItem() {
        Fixture fixture = fixture("PAC-PROC-002", "8100000000002", "8100000000102");
        procedures.saveAndFlush(procedure(fixture, 1, NOW));

        assertThatThrownBy(() -> procedures.saveAndFlush(procedure(fixture, 2, NOW.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidCompletionState() {
        Fixture fixture = fixture("PAC-PROC-003", "8100000000003", "8100000000103");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO treatment_procedures
                    (id, treatment_plan_id, treatment_plan_item_id, patient_id, professional_id,
                     procedure_name, sequence_number, status, performed_at, completed_at)
                VALUES (:id, :plan, :item, :patient, :professional,
                        'Procedimiento', 1, 'COMPLETED', :performed, NULL)
                """).setParameter("id", UUID.randomUUID())
                .setParameter("plan", fixture.plan().getId())
                .setParameter("item", fixture.item().getId())
                .setParameter("patient", fixture.patient().getId())
                .setParameter("professional", fixture.dentist().getId())
                .setParameter("performed", NOW)
                .executeUpdate()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void seedsProcedurePermissionsForExpectedRolesOnly() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'TREATMENT_PROCEDURE_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "TREATMENT_PROCEDURE_READ:ADMINISTRATOR",
                "TREATMENT_PROCEDURE_READ:DENTIST",
                "TREATMENT_PROCEDURE_READ:ASSISTANT",
                "TREATMENT_PROCEDURE_EXECUTE:DENTIST",
                "TREATMENT_PROCEDURE_COMPLETE:DENTIST");
    }

    private Fixture fixture(String code, String dpi, String cui) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente Prueba");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("55550000");
        patient.setCity("Ciudad");
        patient.setAddress("Dirección");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        patients.saveAndFlush(patient);

        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        User dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setUsername("dentist-" + cui);
        dentist.setEmail(cui + "@example.com");
        dentist.setCui(cui);
        dentist.setFullName("Dra. Prueba");
        dentist.setPasswordHash("hash");
        dentist.setStatus(UserStatus.ACTIVE);
        dentist.setRoles(Set.of(dentistRole));
        dentist.setCreatedAt(NOW);
        dentist.setUpdatedAt(NOW);
        users.saveAndFlush(dentist);

        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, dentist, "Plan aprobado", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(120), NOW.minusSeconds(120));
        TreatmentPlanItem item = new TreatmentPlanItem(UUID.randomUUID(), "Restauración", "16", 2,
                new BigDecimal("350.00"), 0);
        plan.addItem(item);
        plan.approve(NOW.minusSeconds(90));
        plans.saveAndFlush(plan);
        return new Fixture(patient, dentist, plan, item);
    }

    private TreatmentProcedure procedure(Fixture fixture, int sequence, Instant performedAt) {
        return new TreatmentProcedure(UUID.randomUUID(), fixture.plan(), fixture.item(), fixture.patient(),
                fixture.dentist(), fixture.item().getName(), fixture.item().getTooth(), sequence,
                "Observación", TreatmentProcedureStatus.IN_PROGRESS, performedAt);
    }

    private record Fixture(Patient patient, User dentist, TreatmentPlan plan, TreatmentPlanItem item) {
    }
}
