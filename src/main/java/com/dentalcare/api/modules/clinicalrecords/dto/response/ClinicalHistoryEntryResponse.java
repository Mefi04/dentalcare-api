package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ClinicalHistoryEntryResponse(
        UUID id,
        String action,
        String category,
        String description,
        String author,
        Instant timestamp) {
}
