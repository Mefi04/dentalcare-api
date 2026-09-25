package com.dentalcare.api.modules.patients.dto.response;

public record PatientEmergencyContactResponse(
        String name,
        String phone,
        String relationship) {
}
