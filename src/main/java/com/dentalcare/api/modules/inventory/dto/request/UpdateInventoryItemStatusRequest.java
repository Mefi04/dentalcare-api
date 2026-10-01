package com.dentalcare.api.modules.inventory.dto.request;

import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInventoryItemStatusRequest(
        @NotNull(message = "Status is required")
        InventoryItemStatus status
) {
}
