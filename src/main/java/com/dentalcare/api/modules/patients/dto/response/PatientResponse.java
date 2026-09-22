package com.dentalcare.api.modules.patients.dto.response;

import com.dentalcare.api.modules.patients.model.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String code,
        String name,
        String dpi,
        LocalDate birthDate,
        Gender gender,
        String phone,
        String email,
        String city,
        String address,
        String emergencyContact,
        String emergencyPhone,
        String billingName,
        String nit,
        String billingAddress,
        String guardianName,
        String guardianRelationship,
        String guardianPhone,
        Instant createdAt,
        Instant updatedAt) {
}
