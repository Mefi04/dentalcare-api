package com.dentalcare.api.modules.inventory.dto.response;

import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        String code,
        String name,
        String description,
        InventoryItemType type,
        String category,
        InventoryItemStatus status,
        String unit,
        Integer currentStock,
        Integer minimumStock,
        LocalDate expirationDate,
        String location,
        Integer totalQuantity,
        Integer availableQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}
