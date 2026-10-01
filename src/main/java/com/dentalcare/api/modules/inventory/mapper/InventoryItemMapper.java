package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemMapper {

    public InventoryItem toEntity(CreateInventoryItemRequest request) {
        if (request == null) {
            return null;
        }
        InventoryItem item = new InventoryItem(
                null,
                request.code(),
                request.name(),
                request.description(),
                request.type(),
                request.category(),
                InventoryItemStatus.ACTIVE,
                null, null, null, null,
                null, null, null,
                null, null
        );

        if (request.type() == InventoryItemType.CONSUMABLE) {
            item.setUnit(request.unit());
            item.setCurrentStock(request.currentStock());
            item.setMinimumStock(request.minimumStock());
            item.setExpirationDate(request.expirationDate());
        } else if (request.type() == InventoryItemType.INSTRUMENT) {
            item.setLocation(request.location());
            item.setTotalQuantity(request.totalQuantity());
            item.setAvailableQuantity(request.availableQuantity());
        }

        return item;
    }

    public void updateEntity(InventoryItem item, UpdateInventoryItemRequest request) {
        if (item == null || request == null) {
            return;
        }
        if (request.code() != null && !request.code().isBlank()) {
            item.setCode(request.code());
        }
        item.setName(request.name());
        item.setDescription(request.description());
        item.setCategory(request.category());
        if (request.status() != null) {
            item.setStatus(request.status());
        }

        if (item.getType() == InventoryItemType.CONSUMABLE) {
            if (request.unit() != null) {
                item.setUnit(request.unit());
            }
            if (request.minimumStock() != null) {
                item.setMinimumStock(request.minimumStock());
            }
            item.setExpirationDate(request.expirationDate());
        } else if (item.getType() == InventoryItemType.INSTRUMENT) {
            if (request.location() != null) {
                item.setLocation(request.location());
            }
        }
    }

    public InventoryItemResponse toResponse(InventoryItem item) {
        if (item == null) {
            return null;
        }
        return new InventoryItemResponse(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getDescription(),
                item.getType(),
                item.getCategory(),
                item.getStatus(),
                item.getUnit(),
                item.getCurrentStock(),
                item.getMinimumStock(),
                item.getExpirationDate(),
                item.getLocation(),
                item.getTotalQuantity(),
                item.getAvailableQuantity(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
