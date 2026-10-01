package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.service.InventoryMovementService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory movements", description = "Auditable inventory movements and Kardex")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    @Operation(summary = "Register an inventory movement",
            description = "Registers an entry, permanent exit, or absolute physical-count adjustment atomically.")
    @PostMapping("/items/{itemId}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<InventoryMovementResponse> register(
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateInventoryMovementRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.register(itemId, request, principal.userId()));
    }

    @Operation(summary = "Get the Kardex for one inventory item")
    @GetMapping("/items/{itemId}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<Page<InventoryMovementResponse>> findByItem(
            @PathVariable UUID itemId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) UUID performedBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryMovementService.findByItem(
                itemId, type, performedBy, from, to, page, size));
    }

    @Operation(summary = "Search the global inventory Kardex")
    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<Page<InventoryMovementResponse>> findAll(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) UUID performedBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryMovementService.findAll(
                itemId, type, performedBy, from, to, page, size));
    }
}
