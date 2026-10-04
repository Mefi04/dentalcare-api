package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetItem;
import com.dentalcare.api.modules.treatments.model.TreatmentConsent;
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
class TreatmentBudgetConsentRepositoryIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TreatmentPlanRepository plans;
    @Autowired private TreatmentBudgetRepository budgets;
    @Autowired private TreatmentConsentRepository consents;
    @Autowired private PatientRepository patients;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsBudgetSnapshotAndTraceableConsentLifecycle() {
        Fixture fixture = fixture("9100000000001");
        TreatmentBudget budget = budget(fixture, 1);
        budgets.saveAndFlush(budget);
        TreatmentConsent consent = new TreatmentConsent(UUID.randomUUID(), fixture.plan(), fixture.patient(),
                budget, "DC-CONSENT-v1", "Texto íntegro aceptado", fixture.dentist(), NOW);
        consents.saveAndFlush(consent);
        consent.accept(fixture.dentist(), NOW.plusSeconds(30));
        consents.saveAndFlush(consent);
        entityManager.clear();

        TreatmentBudget loadedBudget = budgets.findDetailedById(budget.getId()).orElseThrow();
        TreatmentConsent loadedConsent = consents.findDetailedById(consent.getId()).orElseThrow();
        assertThat(loadedBudget.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Restauración");
            assertThat(item.getSubtotal()).isEqualByComparingTo("700.00");
        });
        assertThat(loadedConsent.getPatient().getId()).isEqualTo(fixture.patient().getId());
        assertThat(loadedConsent.getTreatmentBudget().getId()).isEqualTo(budget.getId());
        assertThat(loadedConsent.getAcceptedBy().getId()).isEqualTo(fixture.dentist().getId());
        assertThat(loadedConsent.getAcceptedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void databasePreventsTwoActiveBudgetsForSamePlan() {
        Fixture fixture = fixture("9100000000002");
        budgets.saveAndFlush(budget(fixture, 1));

        assertThatThrownBy(() -> budgets.saveAndFlush(budget(fixture, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsOnlyIntendedRolePermissions() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'TREATMENT_BUDGET_%' OR p.code LIKE 'TREATMENT_CONSENT_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "TREATMENT_BUDGET_READ:ADMINISTRATOR", "TREATMENT_BUDGET_READ:DENTIST",
                "TREATMENT_BUDGET_READ:ASSISTANT", "TREATMENT_BUDGET_CREATE:DENTIST",
                "TREATMENT_BUDGET_CREATE:ASSISTANT", "TREATMENT_BUDGET_DECIDE:DENTIST",
                "TREATMENT_CONSENT_READ:ADMINISTRATOR", "TREATMENT_CONSENT_READ:DENTIST",
                "TREATMENT_CONSENT_READ:ASSISTANT", "TREATMENT_CONSENT_CREATE:DENTIST",
                "TREATMENT_CONSENT_CREATE:ASSISTANT", "TREATMENT_CONSENT_ACCEPT:DENTIST",
                "TREATMENT_CONSENT_REVOKE:DENTIST");
    }

    private TreatmentBudget budget(Fixture fixture, int version) {
        TreatmentPlanItem source = fixture.plan().getItems().getFirst();
        TreatmentBudget budget = new TreatmentBudget(UUID.randomUUID(), fixture.plan(), fixture.patient(),
                version, fixture.plan().getUpdatedAt(), new BigDecimal("700.00"),
                new BigDecimal("700.00"), fixture.dentist(), NOW);
        budget.replaceItems(List.of(new TreatmentBudgetItem(UUID.randomUUID(), source.getId(),
                source.getName(), source.getTooth(), source.getQuantity(), source.getUnitPrice(),
                new BigDecimal("700.00"), source.getPosition())));
        return budget;
    }

    private Fixture fixture(String suffix) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-" + suffix);
        patient.setName("Paciente prueba");
        patient.setDpi(suffix);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-0000");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        patient = patients.saveAndFlush(patient);

        Role role = roles.findByCode("DENTIST").orElseThrow();
        User dentist = new User(UUID.randomUUID(), "dentist-" + suffix, "Dra. Prueba",
                "dentist-" + suffix + "@example.test", suffix, "hash", UserStatus.ACTIVE, NOW, NOW);
        dentist.setRoles(Set.of(role));
        dentist = users.saveAndFlush(dentist);

        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, dentist, "Plan", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(60), NOW.minusSeconds(60));
        plan.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Restauración", "16", 2,
                new BigDecimal("350.00"), 0));
        plan.approve(NOW.minusSeconds(30));
        plan = plans.saveAndFlush(plan);
        return new Fixture(patient, dentist, plan);
    }

    private record Fixture(Patient patient, User dentist, TreatmentPlan plan) { }
}
