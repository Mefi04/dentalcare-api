package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentItemStatus;
import com.dentalcare.api.modules.treatments.mapper.PatientTreatmentPlanMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentProcedureRepository;
import com.dentalcare.api.modules.users.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientTreatmentPlanServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Mock private PatientRepository patients;
    @Mock private TreatmentPlanRepository plans;
    @Mock private TreatmentProcedureRepository procedures;

    private PatientTreatmentPlanServiceImpl service;
    private UUID userId;
    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        service = new PatientTreatmentPlanServiceImpl(
                patients, plans, procedures, new PatientTreatmentPlanMapper());
        userId = UUID.randomUUID();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setFullName("Dra. Ana López");
    }

    @Test
    void listsOnlyApprovedOwnedPlansWithRealDerivedProgress() {
        TreatmentPlan plan = approvedPlan(2);
        TreatmentPlanItem item = plan.getItems().getFirst();
        TreatmentProcedure completed = procedure(plan, item, TreatmentProcedureStatus.COMPLETED, 1);
        TreatmentProcedure active = procedure(plan, item, TreatmentProcedureStatus.IN_PROGRESS, 2);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(plans.findApprovedIdsByPatientId(org.mockito.ArgumentMatchers.eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(plan.getId())));
        when(plans.findDetailedByIdIn(List.of(plan.getId()))).thenReturn(List.of(plan));
        when(procedures.findByTreatmentPlanIds(List.of(plan.getId())))
                .thenReturn(List.of(completed, active));

        var response = service.findMine(userId, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        var result = response.getContent().getFirst();
        assertThat(result.professionalFullName()).isEqualTo("Dra. Ana López");
        assertThat(result.plannedQuantity()).isEqualTo(2);
        assertThat(result.completedQuantity()).isEqualTo(1);
        assertThat(result.progressPercentage()).isEqualTo(50);
        assertThat(result.items().getFirst().status()).isEqualTo(PatientTreatmentItemStatus.IN_PROGRESS);
        assertThat(result.items().getFirst().procedures()).hasSize(2);
    }

    @Test
    void returnsValidEmptyPageForPatientWithoutPlans() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(plans.findApprovedIdsByPatientId(org.mockito.ArgumentMatchers.eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.findMine(userId, 0, 20)).isEmpty();
        verify(plans, org.mockito.Mockito.never()).findDetailedByIdIn(any());
        verify(procedures, org.mockito.Mockito.never()).findByTreatmentPlanIds(any());
    }

    @Test
    void returnsOwnedApprovedDetailAndMasksForeignOrDraftPlansAsNotFound() {
        TreatmentPlan own = approvedPlan(1);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(plans.findDetailedById(own.getId())).thenReturn(Optional.of(own));
        when(procedures.findOwnedByPlanId(own.getId(), patient.getId())).thenReturn(List.of());
        assertThat(service.findMineById(userId, own.getId()).id()).isEqualTo(own.getId());

        Patient other = new Patient();
        other.setId(UUID.randomUUID());
        TreatmentPlan foreign = plan(other, TreatmentPlanStatus.APPROVED, 1);
        when(plans.findDetailedById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.findMineById(userId, foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Treatment plan not found");

        TreatmentPlan draft = plan(patient, TreatmentPlanStatus.DRAFT, 1);
        when(plans.findDetailedById(draft.getId())).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.findMineById(userId, draft.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validatesSessionPatientAndPagination() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMine(userId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findMine(null, 0, 20))
                .isInstanceOf(BadRequestException.class);

        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.findMine(userId, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findMine(userId, 0, 0))
                .isInstanceOf(BadRequestException.class);
    }

    private TreatmentPlan approvedPlan(int quantity) {
        return plan(patient, TreatmentPlanStatus.APPROVED, quantity);
    }

    private TreatmentPlan plan(Patient owner, TreatmentPlanStatus status, int quantity) {
        TreatmentPlan value = new TreatmentPlan(UUID.randomUUID(), owner, dentist, "Plan integral", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
        value.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Restauración", "16", quantity,
                new BigDecimal("350.00"), 0));
        if (status == TreatmentPlanStatus.APPROVED) value.approve(NOW.minusSeconds(1800));
        return value;
    }

    private TreatmentProcedure procedure(TreatmentPlan plan, TreatmentPlanItem item,
                                         TreatmentProcedureStatus status, int sequence) {
        TreatmentProcedure value = new TreatmentProcedure(UUID.randomUUID(), plan, item, patient, dentist,
                item.getName(), item.getTooth(), sequence, null,
                TreatmentProcedureStatus.IN_PROGRESS, NOW.minusSeconds(120L - sequence));
        if (status == TreatmentProcedureStatus.COMPLETED) value.complete(null, NOW.minusSeconds(60));
        return value;
    }
}
