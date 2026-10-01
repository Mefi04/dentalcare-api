package com.dentalcare.api.modules.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseItemRequest(
        @JsonProperty("inventoryItemId")
        UUID inventoryItemId,

        @JsonProperty("consumableId")
        UUID consumableId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Unit cost is required")
        @DecimalMin(value = "0.00", message = "Unit cost must be greater than or equal to zero")
        BigDecimal unitCost
) {
    public UUID resolveItemId() {
        return inventoryItemId != null ? inventoryItemId : consumableId;
    }
}
