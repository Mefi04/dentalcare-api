package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.model.InventoryMovement;
import org.springframework.stereotype.Component;

@Component
public class InventoryMovementMapper {

    public InventoryMovementResponse toResponse(InventoryMovement movement) {
        if (movement == null) {
            return null;
        }
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getItem().getId(),
                movement.getItem().getCode(),
                movement.getItem().getName(),
                movement.getItem().getType(),
                movement.getType(),
                movement.getQuantity(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getAvailableBefore(),
                movement.getAvailableAfter(),
                movement.getPerformedBy().getId(),
                movement.getPerformedBy().getUsername(),
                movement.getPerformedBy().getFullName(),
                movement.getObservation(),
                movement.getReference(),
                movement.getCreatedAt()
        );
    }
}
