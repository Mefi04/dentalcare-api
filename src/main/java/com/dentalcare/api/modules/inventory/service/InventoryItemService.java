package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface InventoryItemService {

    Page<InventoryItemResponse> findAll(String search, InventoryItemType type,
                                        String category, InventoryItemStatus status,
                                        int page, int size);

    InventoryItemResponse findById(UUID id);

    InventoryItemResponse create(CreateInventoryItemRequest request);

    InventoryItemResponse update(UUID id, UpdateInventoryItemRequest request);

    InventoryItemResponse updateStatus(UUID id, InventoryItemStatus status);

    void deactivate(UUID id);
}
