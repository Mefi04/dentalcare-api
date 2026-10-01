package com.dentalcare.api.modules.prescriptions.dto.response;

import com.dentalcare.api.modules.prescriptions.model.PrescriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        PrescriptionPatientResponse patient,
        PrescriptionProfessionalResponse professional, String medication, String presentation,
        String dosage, String frequency, String duration, String instructions,
        Instant issuedAt, PrescriptionStatus status
) {
}
