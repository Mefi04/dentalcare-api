package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.dto.request.PrepareTreatmentConsentRequest;
import com.dentalcare.api.modules.treatments.mapper.TreatmentConsentMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentConsent;
import com.dentalcare.api.modules.treatments.model.TreatmentConsentStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentConsentRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentConsentServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

    @Mock private TreatmentConsentRepository consents;
    @Mock private TreatmentBudgetRepository budgets;
    @Mock private TreatmentPlanRepository plans;
    @Mock private UserRepository users;

    private TreatmentConsentServiceImpl service;
    private User actor;
    private TreatmentPlan plan;

    @BeforeEach
    void setUp() {
        service = new TreatmentConsentServiceImpl(consents, budgets, plans, users,
                new TreatmentConsentMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        actor = activeUser();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        plan = new TreatmentPlan(UUID.randomUUID(), patient, actor, "Plan", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(120), NOW.minusSeconds(120));
        plan.approve(NOW.minusSeconds(60));
        org.mockito.Mockito.lenient().when(consents.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void preparesPendingImmutableConsentWithTraceableActor() {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(plans.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(consents.findActiveByPlanIdForUpdate(any(), anyList())).thenReturn(Optional.empty());
        when(budgets.findFirstByTreatmentPlan_IdAndStatusOrderByVersionDesc(
                plan.getId(), TreatmentBudgetStatus.APPROVED)).thenReturn(Optional.empty());

        var response = service.prepare(plan.getId(), actor.getId(),
                new PrepareTreatmentConsentRequest(" v1.2 ", " Acepto el tratamiento informado. "));

        assertThat(response.patientId()).isEqualTo(plan.getPatient().getId());
        assertThat(response.documentVersion()).isEqualTo("v1.2");
        assertThat(response.consentText()).isEqualTo("Acepto el tratamiento informado.");
        assertThat(response.status()).isEqualTo(TreatmentConsentStatus.PENDING);
        assertThat(response.preparedBy().id()).isEqualTo(actor.getId());
        assertThat(response.acceptedAt()).isNull();
    }

    @Test
    void explicitlyAcceptsOnceAndPreservesTraceabilityWhenRevoked() {
        TreatmentConsent consent = consent();
        when(consents.findDetailedByIdForUpdate(consent.getId())).thenReturn(Optional.of(consent));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));

        var accepted = service.accept(consent.getId(), actor.getId());
        assertThat(accepted.status()).isEqualTo(TreatmentConsentStatus.ACCEPTED);
        assertThat(accepted.acceptedBy().id()).isEqualTo(actor.getId());
        assertThat(accepted.acceptedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.accept(consent.getId(), actor.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only pending treatment consents can be accepted");

        var revoked = service.revoke(consent.getId(), actor.getId());
        assertThat(revoked.status()).isEqualTo(TreatmentConsentStatus.REVOKED);
        assertThat(revoked.acceptedAt()).isEqualTo(NOW);
        assertThat(revoked.revokedBy().id()).isEqualTo(actor.getId());
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsDuplicateActiveConsent() {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(plans.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(consents.findActiveByPlanIdForUpdate(any(), anyList())).thenReturn(Optional.of(consent()));

        assertThatThrownBy(() -> service.prepare(plan.getId(), actor.getId(),
                new PrepareTreatmentConsentRequest("v1", "Consentimiento")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Treatment plan already has an active consent");
    }

    private TreatmentConsent consent() {
        return new TreatmentConsent(UUID.randomUUID(), plan, plan.getPatient(), null,
                "v1", "Consentimiento", actor, NOW.minusSeconds(30));
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Dra. Emisora");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
