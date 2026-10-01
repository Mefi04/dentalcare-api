package com.dentalcare.api.modules.inventory.dto.response;

import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;

import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID itemId,
        String itemCode,
        String itemName,
        InventoryItemType itemType,
        InventoryMovementType type,
        Integer quantity,
        Integer stockBefore,
        Integer stockAfter,
        Integer availableBefore,
        Integer availableAfter,
        UUID performedById,
        String performedByUsername,
        String performedByName,
        String observation,
        String reference,
        Instant createdAt
) {
}
