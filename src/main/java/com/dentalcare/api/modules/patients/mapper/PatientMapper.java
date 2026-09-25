package com.dentalcare.api.modules.patients.mapper;

import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientRequest;
import com.dentalcare.api.modules.patients.dto.response.PatientEmergencyContactResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import com.dentalcare.api.modules.patients.dto.response.PatientProfileResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.util.DpiMasker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PatientMapper {

    public Patient toEntity(CreatePatientRequest request) {
        Patient patient = new Patient();
        apply(patient, request.name(), request.dpi(), request.birthDate(), request.gender(), request.phone(), request.email(),
                request.city(), request.address(), request.emergencyContact(), request.emergencyPhone(), request.billingName(),
                request.nit(), request.billingAddress(), request.guardianName(), request.guardianRelationship(), request.guardianPhone());
        return patient;
    }

    public void updateEntity(Patient patient, UpdatePatientRequest request) {
        apply(patient, request.name(), request.dpi(), request.birthDate(), request.gender(), request.phone(), request.email(),
                request.city(), request.address(), request.emergencyContact(), request.emergencyPhone(), request.billingName(),
                request.nit(), request.billingAddress(), request.guardianName(), request.guardianRelationship(), request.guardianPhone());
    }

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getCode(), patient.getName(), patient.getDpi(), patient.getBirthDate(),
                patient.getGender(), patient.getPhone(), patient.getEmail(), patient.getCity(), patient.getAddress(),
                patient.getEmergencyContact(), patient.getEmergencyPhone(), patient.getBillingName(), patient.getNit(),
                patient.getBillingAddress(), patient.getGuardianName(), patient.getGuardianRelationship(), patient.getGuardianPhone(),
                patient.getCreatedAt(), patient.getUpdatedAt());
    }

    public PatientProfileResponse toProfileResponse(Patient patient) {
        return new PatientProfileResponse(
                patient.getName(),
                DpiMasker.mask(patient.getDpi()),
                patient.getBirthDate(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                mapEmergencyContact(patient));
    }

    public PatientHealthResponse toHealthResponse(Patient patient) {
        return new PatientHealthResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                PatientHealthStatus.EMPTY);
    }

    private PatientEmergencyContactResponse mapEmergencyContact(Patient patient) {
        if (patient.getEmergencyContact() == null && patient.getEmergencyPhone() == null) {
            return null;
        }
        return new PatientEmergencyContactResponse(
                patient.getEmergencyContact(),
                patient.getEmergencyPhone(),
                null);
    }

    private void apply(Patient patient, String name, String dpi, java.time.LocalDate birthDate,
                       com.dentalcare.api.modules.patients.model.Gender gender, String phone, String email, String city,
                       String address, String emergencyContact, String emergencyPhone, String billingName, String nit,
                       String billingAddress, String guardianName, String guardianRelationship, String guardianPhone) {
        patient.setName(name);
        patient.setDpi(dpi);
        patient.setBirthDate(birthDate);
        patient.setGender(gender);
        patient.setPhone(phone);
        patient.setEmail(email);
        patient.setCity(city);
        patient.setAddress(address);
        patient.setEmergencyContact(emergencyContact);
        patient.setEmergencyPhone(emergencyPhone);
        patient.setBillingName(billingName);
        patient.setNit(nit);
        patient.setBillingAddress(billingAddress);
        patient.setGuardianName(guardianName);
        patient.setGuardianRelationship(guardianRelationship);
        patient.setGuardianPhone(guardianPhone);
    }
}
