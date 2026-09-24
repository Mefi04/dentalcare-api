package com.dentalcare.api.modules.patients.dto.response;

import com.dentalcare.api.modules.users.model.UserStatus;

import java.util.UUID;

public record CreatePatientAccessResponse(
        UUID patientId,
        UUID userId,
        String username,
        UserStatus status,
        String temporaryPassword) {
}
