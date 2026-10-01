package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

public interface InventoryMovementService {

    InventoryMovementResponse register(UUID itemId, CreateInventoryMovementRequest request,
                                       UUID authenticatedUserId);

    Page<InventoryMovementResponse> findAll(UUID itemId, InventoryMovementType type,
                                            UUID performedBy, Instant from, Instant to,
                                            int page, int size);

    Page<InventoryMovementResponse> findByItem(UUID itemId, InventoryMovementType type,
                                               UUID performedBy, Instant from, Instant to,
                                               int page, int size);
}
