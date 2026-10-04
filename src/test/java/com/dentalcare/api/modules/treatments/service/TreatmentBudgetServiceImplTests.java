package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.mapper.TreatmentBudgetMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentBudgetServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

    @Mock private TreatmentBudgetRepository budgets;
    @Mock private TreatmentPlanRepository plans;
    @Mock private UserRepository users;

    private TreatmentBudgetServiceImpl service;
    private User actor;
    private TreatmentPlan approvedPlan;

    @BeforeEach
    void setUp() {
        service = new TreatmentBudgetServiceImpl(budgets, plans, users, new TreatmentBudgetMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        actor = activeUser("Dra. Emisora");
        approvedPlan = plan(TreatmentPlanStatus.APPROVED);
        approvedPlan.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Restauración", "16", 2,
                new BigDecimal("350.00"), 0));
        approvedPlan.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Radiografía", null, 1,
                new BigDecimal("125.50"), 1));
        org.mockito.Mockito.lenient().when(budgets.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generatesImmutableSnapshotAndCalculatesTotalsOnlyFromPlan() {
        when(plans.findDetailedByIdForUpdate(approvedPlan.getId())).thenReturn(Optional.of(approvedPlan));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(budgets.findActiveByPlanIdForUpdate(any(), anyList())).thenReturn(Optional.empty());
        when(budgets.findMaxVersionByPlanId(approvedPlan.getId())).thenReturn(2);

        var response = service.generate(approvedPlan.getId(), actor.getId());

        ArgumentCaptor<TreatmentBudget> captor = ArgumentCaptor.forClass(TreatmentBudget.class);
        verify(budgets).saveAndFlush(captor.capture());
        TreatmentBudget persisted = captor.getValue();
        assertThat(persisted.getPatient()).isSameAs(approvedPlan.getPatient());
        assertThat(persisted.getVersion()).isEqualTo(3);
        assertThat(persisted.getSubtotal()).isEqualByComparingTo("825.50");
        assertThat(persisted.getTotal()).isEqualByComparingTo("825.50");
        assertThat(persisted.getItems()).hasSize(2);
        assertThat(response.total()).isEqualByComparingTo("825.50");
        assertThat(response.status()).isEqualTo(TreatmentBudgetStatus.PENDING);
        assertThat(response.generatedBy().id()).isEqualTo(actor.getId());
    }

    @Test
    void rejectsMissingDraftAndDuplicatePlanBudget() {
        UUID missing = UUID.randomUUID();
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(plans.findDetailedByIdForUpdate(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(missing, actor.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        TreatmentPlan draft = plan(TreatmentPlanStatus.DRAFT);
        draft.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Evaluación", null, 1,
                BigDecimal.TEN, 0));
        when(plans.findDetailedByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.generate(draft.getId(), actor.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only approved treatment plans can generate a budget");

        when(plans.findDetailedByIdForUpdate(approvedPlan.getId())).thenReturn(Optional.of(approvedPlan));
        when(budgets.findActiveByPlanIdForUpdate(any(), anyList()))
                .thenReturn(Optional.of(budget(TreatmentBudgetStatus.PENDING)));
        assertThatThrownBy(() -> service.generate(approvedPlan.getId(), actor.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Treatment plan already has an active budget");
    }

    @Test
    void approvesPendingOnceAndRejectsInvalidTransition() {
        TreatmentBudget budget = budget(TreatmentBudgetStatus.PENDING);
        when(budgets.findDetailedByIdForUpdate(budget.getId())).thenReturn(Optional.of(budget));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));

        var response = service.approve(budget.getId(), actor.getId());

        assertThat(response.status()).isEqualTo(TreatmentBudgetStatus.APPROVED);
        assertThat(response.decidedBy().id()).isEqualTo(actor.getId());
        assertThat(response.decidedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.reject(budget.getId(), actor.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only pending treatment budgets can be decided");
    }

    private TreatmentPlan plan(TreatmentPlanStatus status) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, actor, "Plan integral", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(120), NOW.minusSeconds(120));
        if (status == TreatmentPlanStatus.APPROVED) plan.approve(NOW.minusSeconds(60));
        return plan;
    }

    private TreatmentBudget budget(TreatmentBudgetStatus status) {
        TreatmentBudget budget = new TreatmentBudget(UUID.randomUUID(), approvedPlan,
                approvedPlan.getPatient(), 1, approvedPlan.getUpdatedAt(), new BigDecimal("825.50"),
                new BigDecimal("825.50"), actor, NOW.minusSeconds(30));
        if (status == TreatmentBudgetStatus.APPROVED) budget.approve(actor, NOW.minusSeconds(10));
        return budget;
    }

    private User activeUser(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
