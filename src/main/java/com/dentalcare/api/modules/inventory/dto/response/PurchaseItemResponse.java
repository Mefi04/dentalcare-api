package com.dentalcare.api.modules.inventory.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItemResponse(
        UUID id,
        UUID inventoryItemId,
        UUID consumableId,
        String itemCode,
        String itemName,
        String unit,
        Integer quantity,
        BigDecimal unitCost,
        BigDecimal subtotal
) {
}
