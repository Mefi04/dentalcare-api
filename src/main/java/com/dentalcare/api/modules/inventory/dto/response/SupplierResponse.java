package com.dentalcare.api.modules.inventory.dto.response;

import com.dentalcare.api.modules.inventory.model.SupplierStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        String notes,
        SupplierStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
