package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.mapper.InventoryItemMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
public class InventoryItemServiceImpl implements InventoryItemService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final Clock clock;

    @Autowired
    public InventoryItemServiceImpl(InventoryItemRepository inventoryItemRepository,
                                    InventoryItemMapper inventoryItemMapper,
                                    Clock clock) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryItemMapper = inventoryItemMapper;
        this.clock = clock;
    }

    public InventoryItemServiceImpl(InventoryItemRepository inventoryItemRepository,
                                    InventoryItemMapper inventoryItemMapper) {
        this(inventoryItemRepository, inventoryItemMapper, Clock.systemUTC());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> findAll(String search, InventoryItemType type,
                                               String category, InventoryItemStatus status,
                                               int page, int size) {
        validatePage(page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), DEFAULT_SORT);

        Specification<InventoryItem> spec = Specification.unrestricted();

        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(root.get("code")), term)
            ));
        }

        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }

        if (category != null && !category.isBlank()) {
            String categoryTerm = category.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("category")), categoryTerm));
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        return inventoryItemRepository.findAll(spec, pageable)
                .map(inventoryItemMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse findById(UUID id) {
        requireItemId(id);
        return inventoryItemRepository.findById(id)
                .map(inventoryItemMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
    }

    @Override
    @Transactional
    public InventoryItemResponse create(CreateInventoryItemRequest request) {
        if (request == null) {
            throw new BadRequestException("Inventory item data is required");
        }

        String normalizedCode = request.code().trim();
        if (inventoryItemRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("An inventory item with code '" + normalizedCode + "' already exists");
        }

        validateTypeSpecificFieldsForCreate(request);

        InventoryItem item = inventoryItemMapper.toEntity(request);
        item.setId(UUID.randomUUID());
        item.setCode(normalizedCode);
        item.setName(request.name().trim());
        item.setCategory(request.category().trim());
        if (request.description() != null) {
            item.setDescription(request.description().trim());
        }

        Instant now = clock.instant();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return saveItem(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse update(UUID id, UpdateInventoryItemRequest request) {
        requireItemId(id);
        if (request == null) {
            throw new BadRequestException("Update data is required");
        }

        InventoryItem item = findItem(id);

        if (request.code() != null && !request.code().isBlank()) {
            String normalizedCode = request.code().trim();
            if (!normalizedCode.equalsIgnoreCase(item.getCode())
                    && inventoryItemRepository.existsByCodeAndIdNot(normalizedCode, id)) {
                throw new ConflictException("An inventory item with code '" + normalizedCode + "' already exists");
            }
            item.setCode(normalizedCode);
        }

        validateTypeSpecificFieldsForUpdate(item.getType(), request);

        inventoryItemMapper.updateEntity(item, request);
        item.setName(request.name().trim());
        item.setCategory(request.category().trim());
        if (request.description() != null) {
            item.setDescription(request.description().trim());
        }
        item.setUpdatedAt(clock.instant());

        return saveItem(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse updateStatus(UUID id, InventoryItemStatus status) {
        requireItemId(id);
        if (status == null) {
            throw new BadRequestException("Status is required");
        }

        InventoryItem item = findItem(id);
        item.setStatus(status);
        item.setUpdatedAt(clock.instant());

        return saveItem(item);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        updateStatus(id, InventoryItemStatus.INACTIVE);
    }

    private InventoryItem findItem(UUID id) {
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
    }

    private InventoryItemResponse saveItem(InventoryItem item) {
        try {
            InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
            return inventoryItemMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Inventory item data conflicts with an existing record");
        }
    }

    private void validateTypeSpecificFieldsForCreate(CreateInventoryItemRequest request) {
        if (request.type() == InventoryItemType.CONSUMABLE) {
            if (request.unit() == null || request.unit().isBlank()) {
                throw new BadRequestException("Unit is required for consumable items");
            }
            if (request.currentStock() == null || request.currentStock() < 0) {
                throw new BadRequestException("Current stock is required and must not be negative for consumable items");
            }
            if (request.minimumStock() == null || request.minimumStock() < 0) {
                throw new BadRequestException("Minimum stock is required and must not be negative for consumable items");
            }
        } else if (request.type() == InventoryItemType.INSTRUMENT) {
            if (request.location() == null || request.location().isBlank()) {
                throw new BadRequestException("Location is required for instrument items");
            }
            if (request.totalQuantity() == null || request.totalQuantity() < 0) {
                throw new BadRequestException("Total quantity is required and must not be negative for instrument items");
            }
            if (request.availableQuantity() == null || request.availableQuantity() < 0) {
                throw new BadRequestException("Available quantity is required and must not be negative for instrument items");
            }
            if (request.availableQuantity() > request.totalQuantity()) {
                throw new BadRequestException("Available quantity cannot exceed total quantity");
            }
        }
    }

    private void validateTypeSpecificFieldsForUpdate(InventoryItemType type, UpdateInventoryItemRequest request) {
        if (type == InventoryItemType.CONSUMABLE) {
            if (request.unit() != null && request.unit().isBlank()) {
                throw new BadRequestException("Unit must not be blank for consumable items");
            }
            if (request.minimumStock() != null && request.minimumStock() < 0) {
                throw new BadRequestException("Minimum stock must not be negative");
            }
        } else if (type == InventoryItemType.INSTRUMENT) {
            if (request.location() != null && request.location().isBlank()) {
                throw new BadRequestException("Location must not be blank for instrument items");
            }
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
    }

    private void requireItemId(UUID id) {
        if (id == null) {
            throw new BadRequestException("Inventory item id is required");
        }
    }
}
