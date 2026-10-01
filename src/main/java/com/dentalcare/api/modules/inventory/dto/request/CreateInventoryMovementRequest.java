package com.dentalcare.api.modules.inventory.dto.request;

import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInventoryMovementRequest(
        @NotNull(message = "Movement type is required")
        InventoryMovementType type,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @Size(max = 500, message = "Observation must not exceed 500 characters")
        String observation,

        @Size(max = 100, message = "Reference must not exceed 100 characters")
        String reference
) {
}
