package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClinicalPreparationResponse(
        UUID id,
        UUID patientId,
        UUID attentionId,
        ClinicalProfessionalResponse preparedBy,
        String bloodPressure,
        Integer heartRate,
        BigDecimal temperature,
        BigDecimal weight,
        String observations,
        List<String> allergies,
        List<String> currentMedications,
        Instant createdAt,
        Instant updatedAt
) {}
