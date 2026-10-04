package com.dentalcare.api.modules.settings.dto.response;

import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcedureCatalogItemResponse(UUID id, String code, String name, String category,
        Integer durationMinutes, BigDecimal basePrice, ProcedureCatalogItemStatus status,
        UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {}
