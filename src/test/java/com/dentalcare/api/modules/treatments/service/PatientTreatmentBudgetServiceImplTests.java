package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.mapper.PatientTreatmentBudgetMapper;
import com.dentalcare.api.modules.treatments.model.PatientBudgetDecision;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import com.dentalcare.api.modules.users.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientTreatmentBudgetServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    @Mock private PatientRepository patients;
    @Mock private TreatmentBudgetRepository budgets;
    private PatientTreatmentBudgetServiceImpl service;
    private UUID userId;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new PatientTreatmentBudgetServiceImpl(patients, budgets,
                new PatientTreatmentBudgetMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
    }

    @Test
    void listsPublishedOwnedBudgetsAndPreservesBackendTotals() {
        TreatmentBudget budget = publishedBudget(patient);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(budgets.findPublishedIdsByPatientId(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(budget.getId())));
        when(budgets.findPatientDetailedByIdIn(List.of(budget.getId()))).thenReturn(List.of(budget));

        var result = service.findMine(userId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().total()).isEqualByComparingTo("700.00");
        assertThat(result.getContent().getFirst().items()).hasSize(1);
        assertThat(result.getContent().getFirst().decision()).isEqualTo(PatientBudgetDecision.PENDING);
    }

    @Test
    void emptyListDoesNotRunDetailQuery() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(budgets.findPublishedIdsByPatientId(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.findMine(userId, 0, 20)).isEmpty();
        verify(budgets, never()).findPatientDetailedByIdIn(any());
    }

    @Test
    void ownDetailIsReturnedAndForeignBudgetIsMasked() {
        TreatmentBudget budget = publishedBudget(patient);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(budgets.findPublishedOwnedById(budget.getId(), patient.getId())).thenReturn(Optional.of(budget));
        assertThat(service.findMineById(userId, budget.getId()).id()).isEqualTo(budget.getId());

        UUID foreign = UUID.randomUUID();
        when(budgets.findPublishedOwnedById(foreign, patient.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMineById(userId, foreign))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Treatment budget not found");
    }

    @Test
    void patientCanDecideOnceAndDecisionIsPersistedWithAuthenticatedUser() {
        TreatmentBudget accepted = publishedBudget(patient);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(budgets.findPublishedOwnedByIdForUpdate(accepted.getId(), patient.getId()))
                .thenReturn(Optional.of(accepted));
        when(budgets.saveAndFlush(accepted)).thenReturn(accepted);

        var response = service.accept(userId, accepted.getId());

        assertThat(response.decision()).isEqualTo(PatientBudgetDecision.ACCEPTED);
        assertThat(response.patientDecidedAt()).isEqualTo(NOW);
        assertThat(accepted.getPatientDecidedBy().getId()).isEqualTo(userId);
        assertThatThrownBy(() -> service.reject(userId, accepted.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Treatment budget already has a patient decision");
        verify(budgets).saveAndFlush(accepted);
    }

    @Test
    void validatesAuthenticationPaginationAndIds() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMine(userId, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findMine(null, 0, 20)).isInstanceOf(BadRequestException.class);

        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.findMine(userId, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findMine(userId, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findMineById(userId, null)).isInstanceOf(BadRequestException.class);
    }

    private TreatmentBudget publishedBudget(Patient owner) {
        User dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setFullName("Dra. Ana López");
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), owner, dentist, "Plan integral", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(7200), NOW.minusSeconds(7200));
        plan.approve(NOW.minusSeconds(7000));
        TreatmentBudget budget = new TreatmentBudget(UUID.randomUUID(), plan, owner, 1,
                plan.getUpdatedAt(), new BigDecimal("700.00"), new BigDecimal("700.00"),
                dentist, NOW.minusSeconds(3600));
        budget.replaceItems(List.of(new TreatmentBudgetItem(UUID.randomUUID(), UUID.randomUUID(),
                "Restauración", "16", 2, new BigDecimal("350.00"), new BigDecimal("700.00"), 0)));
        budget.approve(dentist, NOW.minusSeconds(1800));
        return budget;
    }
}
