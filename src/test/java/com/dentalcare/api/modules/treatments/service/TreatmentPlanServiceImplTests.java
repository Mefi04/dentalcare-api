package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.request.TreatmentPlanItemRequest;
import com.dentalcare.api.modules.treatments.dto.request.UpdateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.mapper.TreatmentPlanMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
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
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentPlanServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock private TreatmentPlanRepository planRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;

    private TreatmentPlanServiceImpl service;
    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        service = new TreatmentPlanServiceImpl(planRepository, patientRepository, userRepository,
                new TreatmentPlanMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        dentist = professional(UserStatus.ACTIVE, "DENTIST", true);
        org.mockito.Mockito.lenient().when(planRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsDraftWithNormalizedFieldsPositionsAndDerivedTotal() {
        stubPatientAndProfessional();
        var request = new CreateTreatmentPlanRequest("  Plan inicial  ", "  Observación  ", dentist.getId(), List.of(
                item("Resina", " 16 ", 2, "10.25"), item("Profilaxis", "  ", 1, "20.00")));

        var response = service.create(patient.getId(), request);

        ArgumentCaptor<TreatmentPlan> captor = ArgumentCaptor.forClass(TreatmentPlan.class);
        org.mockito.Mockito.verify(planRepository).saveAndFlush(captor.capture());
        TreatmentPlan saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TreatmentPlanStatus.DRAFT);
        assertThat(saved.getName()).isEqualTo("Plan inicial");
        assertThat(saved.getObservations()).isEqualTo("Observación");
        assertThat(saved.getItems()).extracting(item -> item.getPosition()).containsExactly(0, 1);
        assertThat(saved.getItems().get(0).getTooth()).isEqualTo("16");
        assertThat(saved.getItems().get(1).getTooth()).isNull();
        assertThat(response.total()).isEqualByComparingTo("40.50");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("20.50");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMissingPatientOrProfessional() {
        UUID missing = UUID.randomUUID();
        when(patientRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(missing, request(dentist.getId())))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Patient not found");

        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(userRepository.findWithRolesById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(patient.getId(), request(missing)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Professional not found");
    }

    @Test
    void rejectsInactiveNonDentistAndInactiveDentistRole() {
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        for (User invalid : List.of(
                professional(UserStatus.INACTIVE, "DENTIST", true),
                professional(UserStatus.ACTIVE, "ASSISTANT", true),
                professional(UserStatus.ACTIVE, "DENTIST", false))) {
            when(userRepository.findWithRolesById(invalid.getId())).thenReturn(Optional.of(invalid));
            assertThatThrownBy(() -> service.create(patient.getId(), request(invalid.getId())))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Professional is not an active dentist");
        }
    }

    @Test
    void rejectsInvalidItemsAtServiceBoundary() {
        stubPatientAndProfessional();
        assertThatThrownBy(() -> service.create(patient.getId(),
                new CreateTreatmentPlanRequest("Plan", null, dentist.getId(), List.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(patient.getId(),
                new CreateTreatmentPlanRequest("Plan", null, dentist.getId(),
                        List.of(item("Item", null, 0, "10.00")))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(patient.getId(),
                new CreateTreatmentPlanRequest("Plan", null, dentist.getId(),
                        List.of(item("Item", null, 1, "0.00")))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updatesDraftAndReplacesItems() {
        TreatmentPlan plan = plan(TreatmentPlanStatus.DRAFT);
        plan.addItem(new com.dentalcare.api.modules.treatments.model.TreatmentPlanItem(
                UUID.randomUUID(), "Anterior", null, 1, new BigDecimal("5.00"), 0));
        User replacement = professional(UserStatus.ACTIVE, "DENTIST", true);
        when(planRepository.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));
        when(userRepository.findWithRolesById(replacement.getId())).thenReturn(Optional.of(replacement));

        var response = service.update(plan.getId(), new UpdateTreatmentPlanRequest(
                "Nuevo", " ", replacement.getId(), List.of(item("Uno", null, 1, "12.00"),
                item("Dos", "24", 2, "3.00"))));

        assertThat(plan.getName()).isEqualTo("Nuevo");
        assertThat(plan.getObservations()).isNull();
        assertThat(plan.getProfessional()).isSameAs(replacement);
        assertThat(plan.getItems()).extracting(i -> i.getName()).containsExactly("Uno", "Dos");
        assertThat(plan.getItems()).allMatch(i -> i.getTreatmentPlan() == plan);
        assertThat(response.total()).isEqualByComparingTo("18.00");
    }

    @Test
    void rejectsUpdatingApprovedPlanBeforeChangingIt() {
        TreatmentPlan plan = plan(TreatmentPlanStatus.DRAFT);
        plan.approve(NOW.minusSeconds(1));
        when(planRepository.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.update(plan.getId(), new UpdateTreatmentPlanRequest(
                "No", null, dentist.getId(), List.of(item("Item", null, 1, "1.00")))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvesDraftAndRejectsRepeatedApproval() {
        TreatmentPlan plan = plan(TreatmentPlanStatus.DRAFT);
        when(planRepository.findDetailedByIdForUpdate(plan.getId())).thenReturn(Optional.of(plan));

        var response = service.approve(plan.getId());

        assertThat(response.status()).isEqualTo(TreatmentPlanStatus.APPROVED);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.approve(plan.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void reportsMissingDetailAndPatientAndListsOnlyRepositoryPage() {
        UUID missing = UUID.randomUUID();
        when(planRepository.findDetailedById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(missing)).isInstanceOf(ResourceNotFoundException.class);

        when(patientRepository.existsById(missing)).thenReturn(false);
        assertThatThrownBy(() -> service.findByPatient(missing, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);

        when(patientRepository.existsById(patient.getId())).thenReturn(true);
        TreatmentPlan plan = plan(TreatmentPlanStatus.DRAFT);
        when(planRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(plan)));
        assertThat(service.findByPatient(patient.getId(), 0, 20).getContent()).hasSize(1);
    }

    @Test
    void professionalCatalogUsesActiveDentistRepositoryProjection() {
        when(userRepository.findActiveDentists()).thenReturn(List.of(dentist));
        assertThat(service.findProfessionals()).containsExactly(
                new com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse(
                        dentist.getId(), dentist.getFullName()));
    }

    private void stubPatientAndProfessional() {
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(userRepository.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
    }

    private CreateTreatmentPlanRequest request(UUID professionalId) {
        return new CreateTreatmentPlanRequest("Plan", null, professionalId,
                List.of(item("Consulta", null, 1, "100.00")));
    }

    private TreatmentPlanItemRequest item(String name, String tooth, int quantity, String price) {
        return new TreatmentPlanItemRequest(name, tooth, quantity, new BigDecimal(price));
    }

    private TreatmentPlan plan(TreatmentPlanStatus status) {
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, dentist, "Plan", null,
                TreatmentPlanStatus.DRAFT, NOW.minusSeconds(60), NOW.minusSeconds(60));
        plan.addItem(new com.dentalcare.api.modules.treatments.model.TreatmentPlanItem(
                UUID.randomUUID(), "Consulta", null, 1, new BigDecimal("100.00"), 0));
        if (status == TreatmentPlanStatus.APPROVED) plan.approve(NOW.minusSeconds(30));
        return plan;
    }

    private User professional(UserStatus status, String roleCode, boolean roleActive) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Profesional");
        user.setStatus(status);
        user.setRoles(Set.of(new Role(UUID.randomUUID(), roleCode, roleCode, null, roleActive)));
        return user;
    }
}
