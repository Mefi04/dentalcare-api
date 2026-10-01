package com.dentalcare.api.modules.prescriptions.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.prescriptions.dto.request.CreatePrescriptionRequest;
import com.dentalcare.api.modules.prescriptions.mapper.PrescriptionMapper;
import com.dentalcare.api.modules.prescriptions.model.Prescription;
import com.dentalcare.api.modules.prescriptions.model.PrescriptionStatus;
import com.dentalcare.api.modules.prescriptions.repository.PrescriptionRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock private PrescriptionRepository prescriptions;
    @Mock private PatientRepository patients;
    @Mock private UserRepository users;

    private PrescriptionServiceImpl service;
    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        service = new PrescriptionServiceImpl(prescriptions, patients, users, new PrescriptionMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = patient();
        dentist = professional(UserStatus.ACTIVE, "DENTIST", true);
        org.mockito.Mockito.lenient().when(prescriptions.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsIssuedPrescriptionForAuthenticatedActiveDentist() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));

        var response = service.create(patient.getId(), dentist.getId(), request());

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptions).saveAndFlush(captor.capture());
        Prescription saved = captor.getValue();
        assertThat(saved.getPatient()).isSameAs(patient);
        assertThat(saved.getProfessional()).isSameAs(dentist);
        assertThat(saved.getMedication()).isEqualTo("Amoxicilina");
        assertThat(saved.getInstructions()).isEqualTo("Tomar después de comer");
        assertThat(saved.getIssuedAt()).isEqualTo(NOW);
        assertThat(saved.getStatus()).isEqualTo(PrescriptionStatus.ISSUED);
        assertThat(response.professional().id()).isEqualTo(dentist.getId());
    }

    @Test
    void listsAndFindsPersistedPrescriptions() {
        Prescription prescription = prescription();
        when(patients.existsById(patient.getId())).thenReturn(true);
        when(prescriptions.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(prescription)));
        when(prescriptions.findById(prescription.getId())).thenReturn(Optional.of(prescription));

        assertThat(service.findByPatient(patient.getId(), 0, 20).getContent()).hasSize(1);
        assertThat(service.findById(prescription.getId()).medication()).isEqualTo("Amoxicilina");
    }

    @Test
    void patientSelfServiceUsesJwtLinkedPatientAndEnforcesOwnership() {
        UUID userId = UUID.randomUUID();
        Prescription owned = prescription();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(prescriptions.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(owned)));
        when(prescriptions.findByIdAndPatient_Id(owned.getId(), patient.getId()))
                .thenReturn(Optional.of(owned));

        assertThat(service.findMine(userId, 0, 20).getContent()).hasSize(1);
        assertThat(service.findMineById(userId, owned.getId()).id()).isEqualTo(owned.getId());

        UUID foreignId = UUID.randomUUID();
        when(prescriptions.findByIdAndPatient_Id(foreignId, patient.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMineById(userId, foreignId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Prescription not found");
    }

    @Test
    void rejectsProfessionalWithoutActiveDentistRole() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        for (User invalid : List.of(
                professional(UserStatus.INACTIVE, "DENTIST", true),
                professional(UserStatus.ACTIVE, "ASSISTANT", true),
                professional(UserStatus.ACTIVE, "DENTIST", false))) {
            when(users.findWithRolesById(invalid.getId())).thenReturn(Optional.of(invalid));
            assertThatThrownBy(() -> service.create(patient.getId(), invalid.getId(), request()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Professional is not an active dentist");
        }
    }

    private CreatePrescriptionRequest request() {
        return new CreatePrescriptionRequest(" Amoxicilina ", "Tableta 500 mg", "500 mg",
                "Cada 8 horas", "7 días", " Tomar después de comer ");
    }

    private Patient patient() {
        Patient value = new Patient();
        value.setId(UUID.randomUUID());
        value.setCode("PAC-001");
        value.setName("Paciente Prueba");
        return value;
    }

    private User professional(UserStatus status, String roleCode, boolean roleActive) {
        User value = new User();
        value.setId(UUID.randomUUID());
        value.setFullName("Dra. Prueba");
        value.setStatus(status);
        value.setRoles(Set.of(new Role(UUID.randomUUID(), roleCode, roleCode, null, roleActive)));
        return value;
    }

    private Prescription prescription() {
        return new Prescription(UUID.randomUUID(), patient, dentist, "Amoxicilina", "Tableta 500 mg",
                "500 mg", "Cada 8 horas", "7 días", "Tomar después de comer", NOW,
                PrescriptionStatus.ISSUED);
    }
}
