package com.dentalcare.api.modules.patients.dto.response;

import java.time.Instant;
import java.util.List;

public record PatientHealthResponse(
        List<String> allergies,
        List<String> currentMedications,
        List<String> relevantConditions,
        List<String> recentChanges,
        String observations,
        Instant lastUpdated,
        PatientHealthStatus status) {
}
