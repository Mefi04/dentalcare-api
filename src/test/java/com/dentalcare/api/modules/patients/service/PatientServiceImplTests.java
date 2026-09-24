package com.dentalcare.api.modules.patients.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientRequest;
import com.dentalcare.api.modules.patients.mapper.PatientMapper;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientCodeGenerator patientCodeGenerator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PatientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatientServiceImpl(patientRepository, new PatientMapper(), patientCodeGenerator,
                userRepository, roleRepository, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsPatientWithGeneratedIdentityNormalizedDpiAndBillingDefaults() {
        when(patientCodeGenerator.nextCode()).thenReturn("PAC-00001");
        saveReturnsPatient();

        var response = service.create(createRequest(LocalDate.of(1990, 1, 1), "2987 45120 0101", null, null, null));

        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("PAC-00001");
        assertThat(response.dpi()).isEqualTo("2987451200101");
        assertThat(response.billingName()).isEqualTo("Maria Perez");
        assertThat(response.nit()).isEqualTo("CF");
        assertThat(response.billingAddress()).isEqualTo("Guatemala City");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(patientRepository).existsByDpi("2987451200101");
    }

    @Test
    void rejectsInvalidDpiBeforePersisting() {
        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(1990, 1, 1), "2987-A5120-0101", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("DPI must contain exactly 13 digits");

        verify(patientRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateDpi() {
        when(patientRepository.existsByDpi("2987451200101")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(1990, 1, 1), "2987451200101", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A patient with this DPI already exists");
    }

    @Test
    void convertsConcurrentPatientUniqueConstraintFailureIntoSafeConflict() {
        when(patientCodeGenerator.nextCode()).thenReturn("PAC-00001");
        when(patientRepository.saveAndFlush(any(Patient.class))).thenThrow(new DataIntegrityViolationException("internal constraint"));

        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(1990, 1, 1), "2987451200101", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Patient data conflicts with an existing record");
    }

    @Test
    void rejectsFutureBirthDateAndInvalidEmail() {
        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(2026, 9, 23), "2987451200101", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Birth date cannot be in the future");
        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(1990, 1, 1), "2987451200101", null, null, "invalid-email")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email must be valid");
    }

    @Test
    void minorRequiresCompleteGuardianAndAdultDoesNot() {
        LocalDate minorBirthDate = LocalDate.of(2008, 9, 23);
        assertThatThrownBy(() -> service.create(createRequest(minorBirthDate, "2987451200101", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Guardian name is required for minors");

        when(patientCodeGenerator.nextCode()).thenReturn("PAC-00002", "PAC-00003");
        saveReturnsPatient();
        assertThat(service.create(createRequest(minorBirthDate, "2987451200101", "Ana Perez", "Mother", null)).id()).isNotNull();
        assertThat(service.create(createRequest(LocalDate.of(2008, 9, 22), "2987451200101", null, null, null)).id()).isNotNull();
    }

    @Test
    void appliesExactAgeBoundaryForGuardianRequirement() {
        assertThatThrownBy(() -> service.create(createRequest(LocalDate.of(2008, 9, 23), "2987451200101", null, null, null)))
                .isInstanceOf(BadRequestException.class);

        when(patientCodeGenerator.nextCode()).thenReturn("PAC-00001");
        saveReturnsPatient();
        assertThat(service.create(createRequest(LocalDate.of(2008, 9, 22), "2987451200101", null, null, null)).id()).isNotNull();
    }

    @Test
    void updatePreservesImmutableFieldsAndAllowsItsOwnDpi() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        saveReturnsPatient();

        var response = service.update(id, updateRequest(LocalDate.of(1990, 1, 1), "2987 45120 0101"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.code()).isEqualTo("PAC-00001");
        assertThat(response.createdAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(patientRepository, never()).existsByDpi(any());
    }

    @Test
    void updateRejectsDpiOwnedByAnotherPatientAndRevalidatesMinorRule() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        when(patientRepository.existsByDpi("1234567890123")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, updateRequest(LocalDate.of(1990, 1, 1), "1234567890123")))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> service.update(id, updateRequest(LocalDate.of(2008, 9, 23), "2987451200101")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Guardian name is required for minors");
    }

    @Test
    void createsPendingPatientPortalAccessWithOnlyPatientRoleAndNoEmail() {
        UUID patientId = UUID.randomUUID();
        Patient patient = existingPatient(patientId, "2987451200101");
        Role patientRole = new Role(UUID.randomUUID(), "PATIENT", "Paciente", null, true);
        when(patientRepository.findByIdForUpdate(patientId)).thenReturn(Optional.of(patient));
        when(roleRepository.findByCode("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$patient-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.saveAndFlush(patient)).thenReturn(patient);

        var response = service.createAccess(patientId);

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.username()).startsWith("patient-").hasSize(44);
        assertThat(response.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(response.temporaryPassword()).hasSize(16).isNotEqualTo("$2a$patient-hash");
        assertThat(patient.getUser()).isNotNull();
        assertThat(patient.getUser().getFullName()).isEqualTo(patient.getName());
        assertThat(patient.getUser().getCui()).isEqualTo(patient.getDpi());
        assertThat(patient.getUser().getEmail()).isNull();
        assertThat(patient.getUser().getPasswordHash()).isEqualTo("$2a$patient-hash");
        assertThat(patient.getUser().getRoles()).containsExactly(patientRole);
        verify(patientRepository).findByIdForUpdate(patientId);
        verify(passwordEncoder).encode(response.temporaryPassword());
    }

    @Test
    void rejectsPortalAccessForExistingUserCuiAndAlreadyLinkedPatient() {
        UUID patientId = UUID.randomUUID();
        Patient patient = existingPatient(patientId, "2987451200101");
        Role patientRole = new Role(UUID.randomUUID(), "PATIENT", "Paciente", null, true);
        when(patientRepository.findByIdForUpdate(patientId)).thenReturn(Optional.of(patient));
        when(roleRepository.findByCode("PATIENT")).thenReturn(Optional.of(patientRole));
        when(userRepository.existsByCui(patient.getDpi())).thenReturn(true);

        assertThatThrownBy(() -> service.createAccess(patientId))
                .isInstanceOf(ConflictException.class).hasMessage("A user with this CUI already exists");

        patient.setUser(new User());
        assertThatThrownBy(() -> service.createAccess(patientId))
                .isInstanceOf(ConflictException.class).hasMessage("Patient already has portal access");
    }

    @Test
    void rejectsPortalAccessWhenPatientRoleIsMissingOrInactive() {
        UUID patientId = UUID.randomUUID();
        Patient patient = existingPatient(patientId, "2987451200101");
        when(patientRepository.findByIdForUpdate(patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.createAccess(patientId))
                .isInstanceOf(com.dentalcare.api.exception.ResourceNotFoundException.class);

        Role inactiveRole = new Role(UUID.randomUUID(), "PATIENT", "Paciente", null, false);
        when(roleRepository.findByCode("PATIENT")).thenReturn(Optional.of(inactiveRole));
        assertThatThrownBy(() -> service.createAccess(patientId))
                .isInstanceOf(ConflictException.class).hasMessage("Patient role is inactive");
    }

    @Test
    void rejectsDpiChangeAfterPortalAccessExists() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        patient.setUser(new User());
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.update(id, updateRequest(LocalDate.of(1990, 1, 1), "1234567890123")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("DPI cannot be changed after patient portal access has been created");
    }

    @Test
    void resolvesCurrentPatientFromAssociatedUserOnly() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        assertThat(service.findCurrentPatient(userId).id()).isEqualTo(patient.getId());
        verify(patientRepository).findByUser_Id(userId);
    }

    private CreatePatientRequest createRequest(LocalDate birthDate, String dpi, String guardianName,
                                               String guardianRelationship, String email) {
        return new CreatePatientRequest("  Maria Perez  ", dpi, birthDate, Gender.FEMALE, " 5555-1234 ", email,
                " Guatemala ", " Guatemala City ", null, null, null, null, null,
                guardianName, guardianRelationship, guardianName == null ? null : "5555-0000");
    }

    private UpdatePatientRequest updateRequest(LocalDate birthDate, String dpi) {
        return new UpdatePatientRequest("Updated Name", dpi, birthDate, Gender.OTHER, "5555-9999", null,
                null, "Updated address", null, null, null, null, null, null, null, null);
    }

    private Patient existingPatient(UUID id, String dpi) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode("PAC-00001");
        patient.setName("Original Name");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.FEMALE);
        patient.setPhone("5555-0000");
        patient.setCreatedAt(NOW.minusSeconds(60));
        patient.setUpdatedAt(NOW.minusSeconds(60));
        return patient;
    }

    private void saveReturnsPatient() {
        when(patientRepository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
