package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.service.InventoryItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/items")
@Tag(name = "Inventory", description = "Inventory catalog management for consumables and instruments")
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    public InventoryItemController(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @Operation(summary = "List and search inventory items",
            description = "Supports filtering by type, category, status, and text search across name and code.")
    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<Page<InventoryItemResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InventoryItemType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) InventoryItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryItemService.findAll(search, type, category, status, page, size));
    }

    @Operation(summary = "Get an inventory item by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<InventoryItemResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryItemService.findById(id));
    }

    @Operation(summary = "Register a new inventory item",
            description = "Registers either a consumable or an instrument with their specific attributes.")
    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<InventoryItemResponse> create(
            @Valid @RequestBody CreateInventoryItemRequest request) {
        InventoryItemResponse response = inventoryItemService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Update administrative information of an inventory item")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<InventoryItemResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryItemService.update(id, request));
    }

    @Operation(summary = "Update an inventory item status",
            description = "Allows activating or deactivating an item explicitly.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<InventoryItemResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryItemStatusRequest request) {
        return ResponseEntity.ok(inventoryItemService.updateStatus(id, request.status()));
    }

    @Operation(summary = "Deactivate an inventory item",
            description = "Performs logical deactivation (sets status to INACTIVE) without physical deletion.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        inventoryItemService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
