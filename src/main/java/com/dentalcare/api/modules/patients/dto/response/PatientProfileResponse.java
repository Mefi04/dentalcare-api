package com.dentalcare.api.modules.patients.dto.response;

import java.time.LocalDate;

public record PatientProfileResponse(
        String fullName,
        String maskedDpi,
        LocalDate birthDate,
        String phone,
        String email,
        String address,
        PatientEmergencyContactResponse emergencyContact) {
}
