package com.dentalcare.api.modules.settings.dto.response;

import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcedureCatalogItemResponse(UUID id, String code, String name, String category, String description,
        Integer durationMinutes, BigDecimal basePrice, ProcedureCatalogItemStatus status,
        UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {

    public ProcedureCatalogItemResponse(UUID id, String code, String name, String category,
            Integer durationMinutes, BigDecimal basePrice, ProcedureCatalogItemStatus status,
            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this(id, code, name, category, null, durationMinutes, basePrice, status, createdBy, updatedBy, createdAt, updatedAt);
    }
}
