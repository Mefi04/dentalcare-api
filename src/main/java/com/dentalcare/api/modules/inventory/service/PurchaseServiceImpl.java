package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.mapper.PurchaseMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.model.Purchase;
import com.dentalcare.api.modules.inventory.model.PurchaseItem;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.inventory.repository.PurchaseRepository;
import com.dentalcare.api.modules.inventory.repository.SupplierRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PurchaseServiceImpl implements PurchaseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("purchaseDate"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserRepository userRepository;
    private final InventoryMovementService inventoryMovementService;
    private final PurchaseMapper purchaseMapper;
    private final Clock clock;

    public PurchaseServiceImpl(PurchaseRepository purchaseRepository,
                               SupplierRepository supplierRepository,
                               InventoryItemRepository inventoryItemRepository,
                               UserRepository userRepository,
                               InventoryMovementService inventoryMovementService,
                               PurchaseMapper purchaseMapper,
                               Clock clock) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.userRepository = userRepository;
        this.inventoryMovementService = inventoryMovementService;
        this.purchaseMapper = purchaseMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseResponse> findAll(UUID supplierId, PurchaseStatus status,
                                          LocalDate from, LocalDate to,
                                          int page, int size) {
        validateFilters(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), DEFAULT_SORT);

        Specification<Purchase> specification = Specification.unrestricted();
        if (supplierId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("supplier").get("id"), supplierId));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
        }

        return purchaseRepository.findAll(specification, pageable)
                .map(purchaseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse findById(UUID id) {
        requireId(id, "Purchase id is required");
        Purchase purchase = purchaseRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        return purchaseMapper.toResponse(purchase);
    }

    @Override
    @Transactional
    public PurchaseResponse create(CreatePurchaseRequest request, UUID authenticatedUserId) {
        requireId(authenticatedUserId, "Authentication is required");
        validateCreateRequest(request);

        User createdBy = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new ConflictException("Cannot create a purchase for an inactive supplier");
        }

        Instant now = clock.instant();
        String code = resolveCode(request.code(), now);

        Purchase purchase = new Purchase(
                UUID.randomUUID(),
                code,
                supplier,
                PurchaseStatus.PENDING,
                request.purchaseDate(),
                normalize(request.reference()),
                normalize(request.observation()),
                createdBy,
                now
        );

        Set<UUID> seenItems = new HashSet<>();
        for (CreatePurchaseItemRequest itemReq : request.items()) {
            UUID itemId = itemReq.resolveItemId();
            if (itemId == null) {
                throw new BadRequestException("Inventory item id is required for purchase item");
            }
            if (!seenItems.add(itemId)) {
                throw new BadRequestException("Duplicate inventory items are not allowed in the same purchase");
            }
            if (itemReq.quantity() == null || itemReq.quantity() <= 0) {
                throw new BadRequestException("Item quantity must be greater than zero");
            }
            if (itemReq.unitCost() == null || itemReq.unitCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Item unit cost must be greater than or equal to zero");
            }
        }

        for (CreatePurchaseItemRequest itemReq : request.items()) {
            UUID itemId = itemReq.resolveItemId();
            InventoryItem item = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + itemId));

            if (item.getStatus() != InventoryItemStatus.ACTIVE) {
                throw new ConflictException("Cannot include inactive inventory item in a purchase: " + item.getName());
            }
            if (item.getType() != InventoryItemType.CONSUMABLE) {
                throw new BadRequestException("Purchases can only be registered for consumable inventory items");
            }

            PurchaseItem purchaseItem = new PurchaseItem(
                    UUID.randomUUID(),
                    purchase,
                    item,
                    itemReq.quantity(),
                    itemReq.unitCost()
            );
            purchase.addItem(purchaseItem);
        }

        Purchase saved = purchaseRepository.save(purchase);
        return purchaseMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PurchaseResponse receive(UUID id, UUID authenticatedUserId) {
        requireId(id, "Purchase id is required");
        requireId(authenticatedUserId, "Authentication is required");

        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));

        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            throw new ConflictException("Purchase has already been received");
        }

        User receivedBy = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        if (purchase.getItems() == null || purchase.getItems().isEmpty()) {
            throw new BadRequestException("Purchase has no items to receive");
        }

        for (PurchaseItem item : purchase.getItems()) {
            InventoryItem invItem = item.getInventoryItem();
            if (invItem.getType() != InventoryItemType.CONSUMABLE) {
                throw new BadRequestException("Purchases can only be received for consumable inventory items");
            }

            CreateInventoryMovementRequest movementRequest = new CreateInventoryMovementRequest(
                    InventoryMovementType.ENTRY,
                    item.getQuantity(),
                    "Recepción de compra " + purchase.getCode(),
                    purchase.getCode()
            );

            inventoryMovementService.register(invItem.getId(), movementRequest, authenticatedUserId);
        }

        Instant now = clock.instant();
        purchase.setStatus(PurchaseStatus.RECEIVED);
        purchase.setReceivedBy(receivedBy);
        purchase.setReceivedAt(now);

        Purchase saved = purchaseRepository.save(purchase);
        return purchaseMapper.toResponse(saved);
    }

    private String resolveCode(String requestedCode, Instant now) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String trimmed = requestedCode.trim();
            if (purchaseRepository.existsByCode(trimmed)) {
                throw new ConflictException("Purchase code already exists: " + trimmed);
            }
            return trimmed;
        }

        Long seq = purchaseRepository.getNextPurchaseCodeSeq();
        int year = LocalDate.ofInstant(now, ZoneOffset.UTC).getYear();
        return String.format("PUR-%d-%06d", year, seq);
    }

    private void validateCreateRequest(CreatePurchaseRequest request) {
        if (request == null) {
            throw new BadRequestException("Purchase data is required");
        }
        if (request.supplierId() == null) {
            throw new BadRequestException("Supplier id is required");
        }
        if (request.purchaseDate() == null) {
            throw new BadRequestException("Purchase date is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Purchase items cannot be empty");
        }
    }

    private void validateFilters(LocalDate from, LocalDate to, int page, int size) {
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
}
