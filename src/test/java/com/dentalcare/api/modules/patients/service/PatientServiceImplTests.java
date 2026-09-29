package com.dentalcare.api.modules.patients.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryService;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientProfileRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientRequest;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import com.dentalcare.api.modules.patients.dto.response.PatientProfileResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
    private MedicalHistoryService medicalHistoryService;
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
        service = new PatientServiceImpl(patientRepository, new PatientMapper(), medicalHistoryService, patientCodeGenerator,
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
        assertThat(response.portalAccessStatus()).isNull();
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
        assertThat(response.portalAccessStatus()).isNull();
        assertThat(response.createdAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(patientRepository, never()).existsByDpi(any());
    }

    @Test
    void updatePreservesPortalAccessStatusWhenPatientHasLinkedUser() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        patient.setUser(user);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));
        saveReturnsPatient();

        var response = service.update(id, updateRequest(LocalDate.of(1990, 1, 1), "2987451200101"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.portalAccessStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void findByIdReturnsPatientWithNullPortalAccessStatusWhenNoUserLinked() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        var response = service.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.portalAccessStatus()).isNull();
        verify(patientRepository).findById(id);
    }

    @Test
    void findByIdReturnsPatientWithPortalAccessStatusWhenUserLinked() {
        UUID id = UUID.randomUUID();
        Patient patient = existingPatient(id, "2987451200101");
        User user = new User();
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        patient.setUser(user);
        when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

        var response = service.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.portalAccessStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        verify(patientRepository).findById(id);
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
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        patient.setUser(user);
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        PatientResponse response = service.findCurrentPatient(userId);
        assertThat(response.id()).isEqualTo(patient.getId());
        assertThat(response.portalAccessStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(patientRepository).findByUser_Id(userId);
    }

    @Test
    void findCurrentPatientThrowsNotFoundWhenUserHasNoLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCurrentPatient(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void findCurrentPatientProfileReturnsMaskedDpiAndPatientData() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        patient.setEmail("paciente@example.com");
        patient.setAddress("Ciudad de Guatemala");
        patient.setEmergencyContact("Contacto Familiar");
        patient.setEmergencyPhone("5555-4321");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        var response = service.findCurrentPatientProfile(userId);

        assertThat(response.fullName()).isEqualTo("Original Name");
        assertThat(response.maskedDpi()).isEqualTo("*********0101");
        assertThat(response.maskedDpi()).doesNotContain("298745120");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.phone()).isEqualTo("5555-0000");
        assertThat(response.email()).isEqualTo("paciente@example.com");
        assertThat(response.address()).isEqualTo("Ciudad de Guatemala");
        assertThat(response.emergencyContact()).isNotNull();
        assertThat(response.emergencyContact().name()).isEqualTo("Contacto Familiar");
        assertThat(response.emergencyContact().phone()).isEqualTo("5555-4321");
        assertThat(response.emergencyContact().relationship()).isNull();
    }

    @Test
    void findCurrentPatientProfileLeavesOptionalFieldsNullWithoutFictionalStrings() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "1234567890123");
        patient.setEmail(null);
        patient.setAddress(null);
        patient.setEmergencyContact(null);
        patient.setEmergencyPhone(null);
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        var response = service.findCurrentPatientProfile(userId);

        assertThat(response.fullName()).isEqualTo("Original Name");
        assertThat(response.maskedDpi()).isEqualTo("*********0123");
        assertThat(response.email()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.emergencyContact()).isNull();
    }

    @Test
    void findCurrentPatientProfileThrowsNotFoundWhenUserHasNoLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCurrentPatientProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void findCurrentPatientHealthResolvesAssociatedPatientAndReturnsEmptyClinicalState() {
        UUID userId = UUID.randomUUID();
        when(medicalHistoryService.findForAuthenticatedPatient(userId)).thenReturn(emptyHealthResponse());

        var response = service.findCurrentPatientHealth(userId);

        assertThat(response.allergies()).isEmpty();
        assertThat(response.currentMedications()).isEmpty();
        assertThat(response.relevantConditions()).isEmpty();
        assertThat(response.recentChanges()).isEmpty();
        assertThat(response.observations()).isNull();
        assertThat(response.lastUpdated()).isNull();
        assertThat(response.status()).isEqualTo(PatientHealthStatus.EMPTY);
        verify(medicalHistoryService).findForAuthenticatedPatient(userId);
    }

    @Test
    void findCurrentPatientHealthThrowsNotFoundWhenUserHasNoLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(medicalHistoryService.findForAuthenticatedPatient(userId))
                .thenThrow(new ResourceNotFoundException("Patient not found"));

        assertThatThrownBy(() -> service.findCurrentPatientHealth(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void twoPatientsRetrieveOnlyTheirOwnProfileAndHealthIsolatedByUserId() {
        UUID userIdA = UUID.randomUUID();
        UUID userIdB = UUID.randomUUID();

        Patient patientA = existingPatient(UUID.randomUUID(), "2987451200101");
        patientA.setName("Paciente A");
        patientA.setEmail("pacienteA@example.com");

        Patient patientB = existingPatient(UUID.randomUUID(), "1234567890123");
        patientB.setName("Paciente B");
        patientB.setEmail("pacienteB@example.com");

        when(patientRepository.findByUser_Id(userIdA)).thenReturn(Optional.of(patientA));
        when(patientRepository.findByUser_Id(userIdB)).thenReturn(Optional.of(patientB));
        when(medicalHistoryService.findForAuthenticatedPatient(userIdA)).thenReturn(emptyHealthResponse());
        when(medicalHistoryService.findForAuthenticatedPatient(userIdB)).thenReturn(emptyHealthResponse());

        var profileA = service.findCurrentPatientProfile(userIdA);
        var profileB = service.findCurrentPatientProfile(userIdB);

        assertThat(profileA.fullName()).isEqualTo("Paciente A");
        assertThat(profileA.maskedDpi()).isEqualTo("*********0101");
        assertThat(profileA.email()).isEqualTo("pacienteA@example.com");

        assertThat(profileB.fullName()).isEqualTo("Paciente B");
        assertThat(profileB.maskedDpi()).isEqualTo("*********0123");
        assertThat(profileB.email()).isEqualTo("pacienteB@example.com");

        var healthA = service.findCurrentPatientHealth(userIdA);
        var healthB = service.findCurrentPatientHealth(userIdB);

        assertThat(healthA.status()).isEqualTo(PatientHealthStatus.EMPTY);
        assertThat(healthB.status()).isEqualTo(PatientHealthStatus.EMPTY);

        verify(patientRepository).findByUser_Id(userIdA);
        verify(patientRepository).findByUser_Id(userIdB);
        verify(medicalHistoryService).findForAuthenticatedPatient(userIdA);
        verify(medicalHistoryService).findForAuthenticatedPatient(userIdB);
    }

    @Test
    void updateCurrentPatientProfileUpdatesAllowedContactFieldsAndPreservesIdentityFields() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        patient.setBillingName("Billing Corp");
        patient.setNit("12345-6");
        patient.setBillingAddress("Billing St 123");
        patient.setGuardianName("Guardian Name");
        patient.setGuardianRelationship("Father");
        patient.setGuardianPhone("5555-7777");

        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        saveReturnsPatient();

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest(
                "5555-4321", "nuevo@example.com", "Zona 10", "Carlos Perez", "5555-8888");

        PatientProfileResponse response = service.updateCurrentPatientProfile(userId, request);

        assertThat(response.fullName()).isEqualTo("Original Name");
        assertThat(response.maskedDpi()).isEqualTo("*********0101");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.phone()).isEqualTo("5555-4321");
        assertThat(response.email()).isEqualTo("nuevo@example.com");
        assertThat(response.address()).isEqualTo("Zona 10");
        assertThat(response.emergencyContact()).isNotNull();
        assertThat(response.emergencyContact().name()).isEqualTo("Carlos Perez");
        assertThat(response.emergencyContact().phone()).isEqualTo("5555-8888");

        // Verify entity fields
        assertThat(patient.getPhone()).isEqualTo("5555-4321");
        assertThat(patient.getEmail()).isEqualTo("nuevo@example.com");
        assertThat(patient.getAddress()).isEqualTo("Zona 10");
        assertThat(patient.getEmergencyContact()).isEqualTo("Carlos Perez");
        assertThat(patient.getEmergencyPhone()).isEqualTo("5555-8888");
        assertThat(patient.getUpdatedAt()).isEqualTo(NOW);

        // Verify administrative fields were NOT touched (mass assignment protection)
        assertThat(patient.getName()).isEqualTo("Original Name");
        assertThat(patient.getDpi()).isEqualTo("2987451200101");
        assertThat(patient.getCode()).isEqualTo("PAC-00001");
        assertThat(patient.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(patient.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(patient.getBillingName()).isEqualTo("Billing Corp");
        assertThat(patient.getNit()).isEqualTo("12345-6");
        assertThat(patient.getBillingAddress()).isEqualTo("Billing St 123");
        assertThat(patient.getGuardianName()).isEqualTo("Guardian Name");
        assertThat(patient.getGuardianRelationship()).isEqualTo("Father");
        assertThat(patient.getGuardianPhone()).isEqualTo("5555-7777");

        verify(patientRepository).findByUser_Id(userId);
        verify(patientRepository).saveAndFlush(patient);
    }

    @Test
    void updateCurrentPatientProfilePreservesOmittedFields() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        patient.setPhone("5555-0000");
        patient.setEmail("original@example.com");
        patient.setAddress("Calle Antigua");
        patient.setEmergencyContact("Contacto Previo");
        patient.setEmergencyPhone("5555-9999");

        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        saveReturnsPatient();

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setPhone("5555-1111");

        PatientProfileResponse response = service.updateCurrentPatientProfile(userId, request);

        assertThat(response.phone()).isEqualTo("5555-1111");
        assertThat(response.email()).isEqualTo("original@example.com");
        assertThat(response.address()).isEqualTo("Calle Antigua");
        assertThat(response.emergencyContact()).isNotNull();
        assertThat(response.emergencyContact().name()).isEqualTo("Contacto Previo");
        assertThat(response.emergencyContact().phone()).isEqualTo("5555-9999");

        assertThat(patient.getPhone()).isEqualTo("5555-1111");
        assertThat(patient.getEmail()).isEqualTo("original@example.com");
        assertThat(patient.getAddress()).isEqualTo("Calle Antigua");
        assertThat(patient.getEmergencyContact()).isEqualTo("Contacto Previo");
        assertThat(patient.getEmergencyPhone()).isEqualTo("5555-9999");
    }

    @Test
    void updateCurrentPatientProfileClearsFieldsWhenExplicitlyNullOrBlank() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        patient.setEmail("borrar@example.com");
        patient.setAddress("Borrar Direccion");
        patient.setEmergencyContact("Borrar Contacto");
        patient.setEmergencyPhone("5555-0000");

        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        saveReturnsPatient();

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setEmail(null);
        request.setAddress("   ");
        request.setEmergencyContact(null);
        request.setEmergencyPhone("");

        PatientProfileResponse response = service.updateCurrentPatientProfile(userId, request);

        assertThat(response.email()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.emergencyContact()).isNull();

        assertThat(patient.getEmail()).isNull();
        assertThat(patient.getAddress()).isNull();
        assertThat(patient.getEmergencyContact()).isNull();
        assertThat(patient.getEmergencyPhone()).isNull();
    }

    @Test
    void updateCurrentPatientProfileRejectsEmptyRequest() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, new UpdatePatientProfileRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one field must be provided for update");

        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one field must be provided for update");

        verify(patientRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCurrentPatientProfileRejectsBlankOrNullPhone() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest nullPhone = new UpdatePatientProfileRequest();
        nullPhone.setPhone(null);
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, nullPhone))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phone is required");

        UpdatePatientProfileRequest blankPhone = new UpdatePatientProfileRequest();
        blankPhone.setPhone("   ");
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, blankPhone))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phone is required");

        verify(patientRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCurrentPatientProfileRejectsPhoneExceedingMax30() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setPhone("1".repeat(31));
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phone must not exceed 30 characters");
    }

    @Test
    void updateCurrentPatientProfileRejectsInvalidEmail() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setEmail("correo-invalido");
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email must be valid");
    }

    @Test
    void updateCurrentPatientProfileRejectsEmailExceedingMax255() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setEmail("a".repeat(250) + "@test.com");
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email must not exceed 255 characters");
    }

    @Test
    void updateCurrentPatientProfileRejectsAddressExceedingMax255() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setAddress("a".repeat(256));
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Address must not exceed 255 characters");
    }

    @Test
    void updateCurrentPatientProfileRejectsEmergencyContactExceedingMax150() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setEmergencyContact("a".repeat(151));
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Emergency contact must not exceed 150 characters");
    }

    @Test
    void updateCurrentPatientProfileRejectsEmergencyPhoneExceedingMax30() {
        UUID userId = UUID.randomUUID();
        Patient patient = existingPatient(UUID.randomUUID(), "2987451200101");
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.of(patient));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setEmergencyPhone("a".repeat(31));
        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Emergency phone must not exceed 30 characters");
    }

    @Test
    void updateCurrentPatientProfileThrowsNotFoundWhenUserHasNoLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setPhone("5555-1234");

        assertThatThrownBy(() -> service.updateCurrentPatientProfile(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void updateCurrentPatientProfilePreventsIdorBetweenPatients() {
        UUID userIdA = UUID.randomUUID();
        UUID userIdB = UUID.randomUUID();

        Patient patientA = existingPatient(UUID.randomUUID(), "1111111110101");
        patientA.setPhone("5555-0001");
        patientA.setAddress("Direccion A");

        Patient patientB = existingPatient(UUID.randomUUID(), "2222222220101");
        patientB.setPhone("5555-0002");
        patientB.setAddress("Direccion B");

        when(patientRepository.findByUser_Id(userIdA)).thenReturn(Optional.of(patientA));
        saveReturnsPatient();

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest();
        request.setPhone("5555-9999");
        request.setAddress("Nueva Direccion A");

        service.updateCurrentPatientProfile(userIdA, request);

        // Patient A is updated
        assertThat(patientA.getPhone()).isEqualTo("5555-9999");
        assertThat(patientA.getAddress()).isEqualTo("Nueva Direccion A");

        // Patient B is completely untouched
        assertThat(patientB.getPhone()).isEqualTo("5555-0002");
        assertThat(patientB.getAddress()).isEqualTo("Direccion B");

        verify(patientRepository).findByUser_Id(userIdA);
        verify(patientRepository, never()).findByUser_Id(userIdB);
    }

    @Test
    void searchWithoutValueUsesFindAll() {
        PageRequest pageable = PageRequest.of(0, 8);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        service.search(0, 8, null);

        verify(patientRepository).findAll(pageable);
        verify(patientRepository, never()).search(any(), any());
    }

    @Test
    void searchWithEmptyValueUsesFindAll() {
        PageRequest pageable = PageRequest.of(0, 8);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        service.search(0, 8, "");

        verify(patientRepository).findAll(pageable);
        verify(patientRepository, never()).search(any(), any());
    }

    @Test
    void searchWithWhitespaceOnlyUsesFindAll() {
        PageRequest pageable = PageRequest.of(0, 8);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        service.search(0, 8, "   ");

        verify(patientRepository).findAll(pageable);
        verify(patientRepository, never()).search(any(), any());
    }

    @Test
    void searchWithTextUsesCustomSearch() {
        PageRequest pageable = PageRequest.of(0, 8);
        when(patientRepository.search("Diego", pageable)).thenReturn(Page.empty(pageable));

        service.search(0, 8, "  Diego  ");

        verify(patientRepository).search("Diego", pageable);
        verify(patientRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void searchWithSpacedDpiRemovesSpacesBeforeCustomSearch() {
        PageRequest pageable = PageRequest.of(0, 8);
        when(patientRepository.search("2987451200101", pageable)).thenReturn(Page.empty(pageable));

        service.search(0, 8, "2987 45120 0101");

        verify(patientRepository).search("2987451200101", pageable);
        verify(patientRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void searchPreservesPortalAccessStatusInPaginatedResults() {
        PageRequest pageable = PageRequest.of(0, 3);

        Patient patient1 = existingPatient(UUID.randomUUID(), "1111111110101");
        patient1.setUser(null);

        Patient patient2 = existingPatient(UUID.randomUUID(), "2222222220101");
        User user2 = new User();
        user2.setStatus(UserStatus.PENDING_ACTIVATION);
        patient2.setUser(user2);

        Patient patient3 = existingPatient(UUID.randomUUID(), "3333333330101");
        User user3 = new User();
        user3.setStatus(UserStatus.ACTIVE);
        patient3.setUser(user3);

        Page<Patient> patientPage = new PageImpl<>(
                List.of(patient1, patient2, patient3), pageable, 3);
        when(patientRepository.findAll(pageable)).thenReturn(patientPage);

        Page<PatientResponse> responsePage = service.search(0, 3, null);

        assertThat(responsePage.getTotalElements()).isEqualTo(3);
        assertThat(responsePage.getTotalPages()).isEqualTo(1);
        assertThat(responsePage.getContent()).hasSize(3);
        assertThat(responsePage.getContent().get(0).portalAccessStatus()).isNull();
        assertThat(responsePage.getContent().get(1).portalAccessStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(responsePage.getContent().get(2).portalAccessStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(patientRepository).findAll(pageable);
    }

    @Test
    void searchWithTextPreservesPortalAccessStatusInPaginatedResults() {
        PageRequest pageable = PageRequest.of(0, 2);

        Patient patient1 = existingPatient(UUID.randomUUID(), "4444444440101");
        User user1 = new User();
        user1.setStatus(UserStatus.INACTIVE);
        patient1.setUser(user1);

        Patient patient2 = existingPatient(UUID.randomUUID(), "5555555550101");
        User user2 = new User();
        user2.setStatus(UserStatus.LOCKED);
        patient2.setUser(user2);

        Page<Patient> patientPage = new PageImpl<>(
                List.of(patient1, patient2), pageable, 2);
        when(patientRepository.search("Carlos", pageable)).thenReturn(patientPage);

        Page<PatientResponse> responsePage = service.search(0, 2, "Carlos");

        assertThat(responsePage.getTotalElements()).isEqualTo(2);
        assertThat(responsePage.getTotalPages()).isEqualTo(1);
        assertThat(responsePage.getContent()).hasSize(2);
        assertThat(responsePage.getContent().get(0).portalAccessStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(responsePage.getContent().get(1).portalAccessStatus()).isEqualTo(UserStatus.LOCKED);
        verify(patientRepository).search("Carlos", pageable);
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

    private PatientHealthResponse emptyHealthResponse() {
        return new PatientHealthResponse(List.of(), List.of(), List.of(), List.of(), null, null,
                PatientHealthStatus.EMPTY);
    }

    private void saveReturnsPatient() {
        when(patientRepository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
