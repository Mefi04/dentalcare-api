package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.request.CompleteTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.mapper.TreatmentProcedureMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentProcedureRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentProcedureServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-01T15:00:00Z");

    @Mock private TreatmentProcedureRepository procedures;
    @Mock private TreatmentPlanRepository plans;
    @Mock private PatientRepository patients;
    @Mock private UserRepository users;

    private TreatmentProcedureServiceImpl service;
    private Patient patient;
    private User dentist;
    private TreatmentPlan plan;
    private TreatmentPlanItem item;

    @BeforeEach
    void setUp() {
        service = new TreatmentProcedureServiceImpl(procedures, plans, patients, users,
                new TreatmentProcedureMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        dentist = dentist();
        plan = new TreatmentPlan(UUID.randomUUID(), patient, dentist, "Plan", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(120), NOW.minusSeconds(120));
        item = new TreatmentPlanItem(UUID.randomUUID(), "Restauración con resina", "16", 2,
                new BigDecimal("350.00"), 0);
        plan.addItem(item);
        plan.approve(NOW.minusSeconds(60));
        org.mockito.Mockito.lenient().when(procedures.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersOneApprovedPlannedUnitWithAuthenticatedDentistAndSnapshot() {
        when(plans.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(procedures.countByTreatmentPlanItem_IdAndStatus(item.getId(),
                TreatmentProcedureStatus.COMPLETED)).thenReturn(1L);

        var response = service.register(plan.getId(), dentist.getId(),
                new CreateTreatmentProcedureRequest(item.getId(), " Aislamiento absoluto "));

        ArgumentCaptor<TreatmentProcedure> captor = ArgumentCaptor.forClass(TreatmentProcedure.class);
        verify(procedures).saveAndFlush(captor.capture());
        TreatmentProcedure saved = captor.getValue();
        assertThat(saved.getPatient()).isSameAs(patient);
        assertThat(saved.getTreatmentPlan()).isSameAs(plan);
        assertThat(saved.getTreatmentPlanItem()).isSameAs(item);
        assertThat(saved.getProfessional()).isSameAs(dentist);
        assertThat(saved.getProcedureName()).isEqualTo("Restauración con resina");
        assertThat(saved.getSequenceNumber()).isEqualTo(2);
        assertThat(saved.getClinicalObservations()).isEqualTo("Aislamiento absoluto");
        assertThat(response.status()).isEqualTo(TreatmentProcedureStatus.IN_PROGRESS);
        assertThat(response.performedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsDraftForeignItemActiveExecutionAndExhaustedQuantity() {
        TreatmentPlan draft = new TreatmentPlan(UUID.randomUUID(), patient, dentist, "Borrador", null,
                TreatmentPlanStatus.DRAFT, NOW, NOW);
        when(plans.findDetailedByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.register(draft.getId(), dentist.getId(),
                new CreateTreatmentProcedureRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only approved treatment plans can be executed");

        when(plans.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        assertThatThrownBy(() -> service.register(plan.getId(), dentist.getId(),
                new CreateTreatmentProcedureRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Treatment plan item not found");

        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(procedures.existsByTreatmentPlanItem_IdAndStatus(item.getId(),
                TreatmentProcedureStatus.IN_PROGRESS)).thenReturn(true);
        assertThatThrownBy(() -> register()).isInstanceOf(ConflictException.class)
                .hasMessage("Treatment plan item already has a procedure in progress");

        when(procedures.existsByTreatmentPlanItem_IdAndStatus(item.getId(),
                TreatmentProcedureStatus.IN_PROGRESS)).thenReturn(false);
        when(procedures.countByTreatmentPlanItem_IdAndStatus(item.getId(),
                TreatmentProcedureStatus.COMPLETED)).thenReturn(2L);
        assertThatThrownBy(() -> register()).isInstanceOf(ConflictException.class)
                .hasMessage("Planned procedure quantity is already fully completed");
    }

    @Test
    void completesOnlyOnceAndOnlyByStartingDentist() {
        TreatmentProcedure procedure = procedure(dentist, TreatmentProcedureStatus.IN_PROGRESS);
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(procedures.findDetailedByIdForUpdate(procedure.getId())).thenReturn(Optional.of(procedure));

        var response = service.complete(procedure.getId(), dentist.getId(),
                new CompleteTreatmentProcedureRequest(" Sin complicaciones "));

        assertThat(response.status()).isEqualTo(TreatmentProcedureStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(NOW);
        assertThat(response.completionNotes()).isEqualTo("Sin complicaciones");
        assertThatThrownBy(() -> service.complete(procedure.getId(), dentist.getId(), null))
                .isInstanceOf(ConflictException.class).hasMessage("Treatment procedure is already completed");

        User other = dentist();
        TreatmentProcedure another = procedure(dentist, TreatmentProcedureStatus.IN_PROGRESS);
        when(users.findWithRolesById(other.getId())).thenReturn(Optional.of(other));
        when(procedures.findDetailedByIdForUpdate(another.getId())).thenReturn(Optional.of(another));
        assertThatThrownBy(() -> service.complete(another.getId(), other.getId(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private TreatmentProcedureServiceImplTests register() {
        service.register(plan.getId(), dentist.getId(), new CreateTreatmentProcedureRequest(item.getId(), null));
        return this;
    }

    private TreatmentProcedure procedure(User professional, TreatmentProcedureStatus status) {
        TreatmentProcedure procedure = new TreatmentProcedure(UUID.randomUUID(), plan, item, patient,
                professional, item.getName(), item.getTooth(), 1, null,
                TreatmentProcedureStatus.IN_PROGRESS, NOW.minusSeconds(30));
        if (status == TreatmentProcedureStatus.COMPLETED) procedure.complete(null, NOW.minusSeconds(10));
        return procedure;
    }

    private User dentist() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Dra. Prueba");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(new Role(UUID.randomUUID(), "DENTIST", "Dentist", null, true)));
        return user;
    }
}
