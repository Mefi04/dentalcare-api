package com.dentalcare.api.modules.clinicalrecords.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateClinicalDocumentVisibilityRequest(
        @NotNull(message = "La visibilidad es obligatoria") Boolean visible) {
}
