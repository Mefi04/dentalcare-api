package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.mapper.InventoryMovementMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovement;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.inventory.repository.InventoryMovementRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort KARDEX_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserRepository userRepository;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final Clock clock;

    public InventoryMovementServiceImpl(InventoryItemRepository inventoryItemRepository,
                                        InventoryMovementRepository inventoryMovementRepository,
                                        UserRepository userRepository,
                                        InventoryMovementMapper inventoryMovementMapper,
                                        Clock clock) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.userRepository = userRepository;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InventoryMovementResponse register(UUID itemId, CreateInventoryMovementRequest request,
                                              UUID authenticatedUserId) {
        requireId(itemId, "Inventory item id is required");
        requireId(authenticatedUserId, "Authentication is required");
        validateRequest(request);

        InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        if (item.getStatus() != InventoryItemStatus.ACTIVE) {
            throw new ConflictException("Movements cannot be registered for an inactive inventory item");
        }

        User performedBy = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        MovementValues values = item.getType() == InventoryItemType.CONSUMABLE
                ? applyConsumableMovement(item, request)
                : applyInstrumentMovement(item, request);

        Instant now = clock.instant();
        item.setUpdatedAt(now);
        inventoryItemRepository.save(item);

        InventoryMovement movement = new InventoryMovement(
                UUID.randomUUID(),
                item,
                request.type(),
                values.movementQuantity(),
                values.stockBefore(),
                values.stockAfter(),
                values.availableBefore(),
                values.availableAfter(),
                performedBy,
                normalize(request.observation()),
                normalize(request.reference()),
                now
        );
        return inventoryMovementMapper.toResponse(inventoryMovementRepository.saveAndFlush(movement));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> findAll(UUID itemId, InventoryMovementType type,
                                                   UUID performedBy, Instant from, Instant to,
                                                   int page, int size) {
        validateFilters(from, to, page, size);
        return findMovements(itemId, type, performedBy, from, to, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> findByItem(UUID itemId, InventoryMovementType type,
                                                      UUID performedBy, Instant from, Instant to,
                                                      int page, int size) {
        requireId(itemId, "Inventory item id is required");
        validateFilters(from, to, page, size);
        if (!inventoryItemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("Inventory item not found");
        }
        return findMovements(itemId, type, performedBy, from, to, page, size);
    }

    private Page<InventoryMovementResponse> findMovements(UUID itemId, InventoryMovementType type,
                                                           UUID performedBy, Instant from, Instant to,
                                                           int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), KARDEX_SORT);
        Specification<InventoryMovement> specification = Specification.unrestricted();

        if (itemId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("item").get("id"), itemId));
        }
        if (type != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (performedBy != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("performedBy").get("id"), performedBy));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return inventoryMovementRepository.findAll(specification, pageable)
                .map(inventoryMovementMapper::toResponse);
    }

    private MovementValues applyConsumableMovement(InventoryItem item,
                                                    CreateInventoryMovementRequest request) {
        int before = item.getCurrentStock();
        int after = calculateAfter(before, request);
        item.setCurrentStock(after);
        return new MovementValues(movementQuantity(before, after), before, after, null, null);
    }

    private MovementValues applyInstrumentMovement(InventoryItem item,
                                                    CreateInventoryMovementRequest request) {
        int totalBefore = item.getTotalQuantity();
        int availableBefore = item.getAvailableQuantity();
        int totalAfter;
        int availableAfter;

        switch (request.type()) {
            case ENTRY -> {
                totalAfter = safeAdd(totalBefore, request.quantity());
                availableAfter = safeAdd(availableBefore, request.quantity());
            }
            case EXIT -> {
                if (request.quantity() > availableBefore) {
                    throw new ConflictException("Insufficient available instrument quantity");
                }
                totalAfter = totalBefore - request.quantity();
                availableAfter = availableBefore - request.quantity();
            }
            case ADJUSTMENT -> {
                totalAfter = request.quantity();
                if (totalAfter == totalBefore) {
                    throw new BadRequestException("Adjustment target must differ from current quantity");
                }
                int unavailable = totalBefore - availableBefore;
                if (totalAfter < unavailable) {
                    throw new ConflictException(
                            "Adjustment target cannot be lower than the unavailable instrument quantity");
                }
                availableAfter = totalAfter - unavailable;
            }
            default -> throw new BadRequestException("Unsupported inventory movement type");
        }

        item.setTotalQuantity(totalAfter);
        item.setAvailableQuantity(availableAfter);
        return new MovementValues(
                movementQuantity(totalBefore, totalAfter), totalBefore, totalAfter,
                availableBefore, availableAfter);
    }

    private int calculateAfter(int before, CreateInventoryMovementRequest request) {
        return switch (request.type()) {
            case ENTRY -> safeAdd(before, request.quantity());
            case EXIT -> {
                if (request.quantity() > before) {
                    throw new ConflictException("Insufficient inventory stock");
                }
                yield before - request.quantity();
            }
            case ADJUSTMENT -> {
                if (request.quantity() == before) {
                    throw new BadRequestException("Adjustment target must differ from current stock");
                }
                yield request.quantity();
            }
        };
    }

    private int safeAdd(int current, int quantity) {
        long result = (long) current + quantity;
        if (result > Integer.MAX_VALUE) {
            throw new ConflictException("Inventory quantity exceeds the supported maximum");
        }
        return (int) result;
    }

    private int movementQuantity(int before, int after) {
        return Math.abs(after - before);
    }

    private void validateRequest(CreateInventoryMovementRequest request) {
        if (request == null) {
            throw new BadRequestException("Inventory movement data is required");
        }
        if (request.type() == null) {
            throw new BadRequestException("Movement type is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
        if (request.type() == InventoryMovementType.ADJUSTMENT
                && (request.observation() == null || request.observation().isBlank())) {
            throw new BadRequestException("Observation is required for inventory adjustments");
        }
    }

    private void validateFilters(Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not be after to date");
        }
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
    }

    private void requireId(UUID id, String message) {
        if (id == null) {
            throw new BadRequestException(message);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record MovementValues(int movementQuantity, int stockBefore, int stockAfter,
                                  Integer availableBefore, Integer availableAfter) {
    }
}
