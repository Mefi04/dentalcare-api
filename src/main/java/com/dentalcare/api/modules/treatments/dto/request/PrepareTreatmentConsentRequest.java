package com.dentalcare.api.modules.treatments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrepareTreatmentConsentRequest(
        @NotBlank @Size(max = 80) String documentVersion,
        @NotBlank @Size(max = 20000) String consentText) {
}
