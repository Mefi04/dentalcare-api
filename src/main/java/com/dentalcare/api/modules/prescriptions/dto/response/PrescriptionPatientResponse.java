package com.dentalcare.api.modules.prescriptions.dto.response;

import java.util.UUID;

public record PrescriptionPatientResponse(UUID id, String code, String name) {
}
