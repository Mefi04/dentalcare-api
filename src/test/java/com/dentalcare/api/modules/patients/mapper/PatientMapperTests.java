package com.dentalcare.api.modules.patients.mapper;

import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import com.dentalcare.api.modules.patients.dto.response.PatientProfileResponse;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientMapperTests {

    private PatientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PatientMapper();
    }

    @Test
    void toProfileResponseMapsCompletePatientWithMaskedDpiAndNullRelationship() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-00001");
        patient.setName("Carlos Gomez");
        patient.setDpi("2987451200101");
        patient.setBirthDate(LocalDate.of(1994, 3, 14));
        patient.setGender(Gender.MALE);
        patient.setPhone("5555-1234");
        patient.setEmail("carlos@example.com");
        patient.setAddress("Calle 1 2-34 Zona 10");
        patient.setEmergencyContact("Maria Gomez");
        patient.setEmergencyPhone("5555-9876");
        patient.setGuardianName("Tutor Legal");
        patient.setGuardianRelationship("Padre");
        patient.setGuardianPhone("5555-0000");

        PatientProfileResponse response = mapper.toProfileResponse(patient);

        assertThat(response.fullName()).isEqualTo("Carlos Gomez");
        assertThat(response.maskedDpi()).isEqualTo("*********0101");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1994, 3, 14));
        assertThat(response.phone()).isEqualTo("5555-1234");
        assertThat(response.email()).isEqualTo("carlos@example.com");
        assertThat(response.address()).isEqualTo("Calle 1 2-34 Zona 10");
        assertThat(response.emergencyContact()).isNotNull();
        assertThat(response.emergencyContact().name()).isEqualTo("Maria Gomez");
        assertThat(response.emergencyContact().phone()).isEqualTo("5555-9876");
        assertThat(response.emergencyContact().relationship()).isNull();
    }

    @Test
    void toProfileResponseLeavesOptionalFieldsAsNullWithoutInventingValues() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setName("Ana Lopez");
        patient.setDpi("1234567890123");
        patient.setBirthDate(LocalDate.of(2000, 5, 20));
        patient.setGender(Gender.FEMALE);
        patient.setPhone("5555-4321");
        patient.setEmail(null);
        patient.setAddress(null);
        patient.setEmergencyContact(null);
        patient.setEmergencyPhone(null);

        PatientProfileResponse response = mapper.toProfileResponse(patient);

        assertThat(response.fullName()).isEqualTo("Ana Lopez");
        assertThat(response.maskedDpi()).isEqualTo("*********0123");
        assertThat(response.email()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.emergencyContact()).isNull();
    }

    @Test
    void toProfileResponseMapsEmergencyContactWhenOnlyNameOrPhoneIsPresent() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setName("Pedro Ramirez");
        patient.setDpi("1111222230101");
        patient.setBirthDate(LocalDate.of(1985, 8, 10));
        patient.setGender(Gender.MALE);
        patient.setPhone("5555-1111");
        patient.setEmergencyContact("Solo Nombre");
        patient.setEmergencyPhone(null);

        PatientProfileResponse response = mapper.toProfileResponse(patient);

        assertThat(response.emergencyContact()).isNotNull();
        assertThat(response.emergencyContact().name()).isEqualTo("Solo Nombre");
        assertThat(response.emergencyContact().phone()).isNull();
        assertThat(response.emergencyContact().relationship()).isNull();
    }

    @Test
    void toHealthResponseReturnsEmptyClinicalStateWithoutUsingPatientUpdatedAt() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUpdatedAt(Instant.parse("2026-09-24T12:00:00Z"));

        PatientHealthResponse response = mapper.toHealthResponse(patient);

        assertThat(response.allergies()).isEmpty();
        assertThat(response.currentMedications()).isEmpty();
        assertThat(response.relevantConditions()).isEmpty();
        assertThat(response.recentChanges()).isEmpty();
        assertThat(response.observations()).isNull();
        assertThat(response.lastUpdated()).isNull();
        assertThat(response.status()).isEqualTo(PatientHealthStatus.EMPTY);
    }
}
