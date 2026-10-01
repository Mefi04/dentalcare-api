package com.dentalcare.api.modules.treatments.dto.request;

import jakarta.validation.constraints.Size;

public record CompleteTreatmentProcedureRequest(
        @Size(max = 4000, message = "Completion notes must not exceed 4000 characters")
        String completionNotes) {
}
