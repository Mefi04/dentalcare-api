package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.medicalhistory.dto.request.UpdateMedicalHistoryRequest;
import com.dentalcare.api.modules.medicalhistory.mapper.MedicalHistoryMapper;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.medicalhistory.repository.MedicalHistoryRepository;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;

    @Mock
    private PatientRepository patientRepository;

    private MedicalHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicalHistoryServiceImpl(medicalHistoryRepository, patientRepository,
                new MedicalHistoryMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsEmptyHistoryForExistingPatientWithoutClinicalRecord() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(medicalHistoryRepository.findByPatient_Id(patientId)).thenReturn(Optional.empty());

        var response = service.findByPatientId(patientId);

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.allergies()).isEmpty();
        assertThat(response.currentMedications()).isEmpty();
        assertThat(response.relevantConditions()).isEmpty();
        assertThat(response.status()).isEqualTo(PatientHealthStatus.EMPTY);
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void createsHistoryAndNormalizesAndDeduplicatesClinicalValues() {
        Patient patient = patient(UUID.randomUUID());
        when(patientRepository.findByIdForUpdate(patient.getId())).thenReturn(Optional.of(patient));
        when(medicalHistoryRepository.findByPatient_Id(patient.getId())).thenReturn(Optional.empty());
        when(medicalHistoryRepository.saveAndFlush(any(MedicalHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(patient.getId(), new UpdateMedicalHistoryRequest(
                List.of(" Penicilina ", "penicilina", "Látex"),
                List.of(" Metformina 500 mg "),
                List.of(" Diabetes tipo 2 "),
                "  Antecedente informado por el paciente.  "));

        assertThat(response.allergies()).containsExactly("Látex", "Penicilina");
        assertThat(response.currentMedications()).containsExactly("Metformina 500 mg");
        assertThat(response.relevantConditions()).containsExactly("Diabetes tipo 2");
        assertThat(response.observations()).isEqualTo("Antecedente informado por el paciente.");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(PatientHealthStatus.UPDATED);
    }

    @Test
    void replacesExistingHistoryWithoutChangingCreationTimestamp() {
        Patient patient = patient(UUID.randomUUID());
        Instant createdAt = NOW.minusSeconds(3600);
        MedicalHistory history = new MedicalHistory(UUID.randomUUID(), patient, createdAt, createdAt);
        history.replaceAllergies(List.of("Penicilina"));
        when(patientRepository.findByIdForUpdate(patient.getId())).thenReturn(Optional.of(patient));
        when(medicalHistoryRepository.findByPatient_Id(patient.getId())).thenReturn(Optional.of(history));
        when(medicalHistoryRepository.saveAndFlush(history)).thenReturn(history);

        var response = service.update(patient.getId(), new UpdateMedicalHistoryRequest(
                List.of(), List.of(), List.of(), null));

        assertThat(response.allergies()).isEmpty();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(PatientHealthStatus.EMPTY);
    }

    @Test
    void rejectsMissingPatientWithoutWritingHistory() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findByIdForUpdate(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(patientId,
                new UpdateMedicalHistoryRequest(List.of(), List.of(), List.of(), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");

        verify(medicalHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolvesSelfServiceIdentityOnlyFromAuthenticatedUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Patient patientA = patient(UUID.randomUUID());
        Patient patientB = patient(UUID.randomUUID());
        MedicalHistory historyA = history(patientA, "Penicilina");
        MedicalHistory historyB = history(patientB, "Látex");
        when(patientRepository.findByUser_Id(userA)).thenReturn(Optional.of(patientA));
        when(patientRepository.findByUser_Id(userB)).thenReturn(Optional.of(patientB));
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.of(historyA));
        when(medicalHistoryRepository.findByPatient_Id(patientB.getId())).thenReturn(Optional.of(historyB));

        var responseA = service.findForAuthenticatedPatient(userA);
        var responseB = service.findForAuthenticatedPatient(userB);

        assertThat(responseA.allergies()).containsExactly("Penicilina");
        assertThat(responseB.allergies()).containsExactly("Látex");
        verify(patientRepository).findByUser_Id(userA);
        verify(patientRepository).findByUser_Id(userB);
    }

    @Test
    void selfServiceRejectsUserWithoutLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findForAuthenticatedPatient(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
    }

    @Test
    void serviceValidationRejectsNullListsBlankItemsAndOversizedObservations() {
        Patient patient = patient(UUID.randomUUID());
        when(patientRepository.findByIdForUpdate(patient.getId())).thenReturn(Optional.of(patient));
        when(medicalHistoryRepository.findByPatient_Id(patient.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(patient.getId(),
                new UpdateMedicalHistoryRequest(null, List.of(), List.of(), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Allergies are required");

        assertThatThrownBy(() -> service.update(patient.getId(),
                new UpdateMedicalHistoryRequest(List.of(" "), List.of(), List.of(), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Allergies must not contain blank entries");

        assertThatThrownBy(() -> service.update(patient.getId(),
                new UpdateMedicalHistoryRequest(List.of(), List.of(), List.of(), "x".repeat(4001))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Observations must not exceed 4000 characters");
    }

    private Patient patient(UUID id) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode("PAC-00001");
        patient.setName("Paciente Prueba");
        patient.setDpi("2987451200101");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-1234");
        patient.setCreatedAt(NOW.minusSeconds(7200));
        patient.setUpdatedAt(NOW.minusSeconds(7200));
        return patient;
    }

    private MedicalHistory history(Patient patient, String allergy) {
        MedicalHistory history = new MedicalHistory(UUID.randomUUID(), patient, NOW.minusSeconds(60), NOW);
        history.replaceAllergies(List.of(allergy));
        return history;
    }
}
