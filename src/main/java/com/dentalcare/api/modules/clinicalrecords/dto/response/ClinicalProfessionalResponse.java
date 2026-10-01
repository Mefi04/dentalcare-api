package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.util.UUID;

public record ClinicalProfessionalResponse(
        UUID id,
        String fullName) {
}
