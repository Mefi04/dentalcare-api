package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MedicalHistoryResponse(
        UUID patientId,
        List<String> allergies,
        List<String> currentMedications,
        List<String> relevantConditions,
        String observations,
        Instant createdAt,
        Instant updatedAt,
        PatientHealthStatus status) {
}
