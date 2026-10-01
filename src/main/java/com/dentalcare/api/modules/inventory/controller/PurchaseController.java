package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.service.PurchaseService;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/purchases")
@Tag(name = "Inventory Purchases", description = "Management and receipt of inventory purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Operation(summary = "List and filter purchases",
            description = "Supports filtering by supplier, status, date range, and pagination.")
    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<Page<PurchaseResponse>> findAll(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(purchaseService.findAll(supplierId, status, from, to, page, size));
    }

    @Operation(summary = "Get a purchase by ID with items and audit")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<PurchaseResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.findById(id));
    }

    @Operation(summary = "Create an inventory purchase",
            description = "Creates a purchase in PENDING status. Does not modify inventory stock.")
    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<PurchaseResponse> create(
            @Valid @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseService.create(request, principal.userId()));
    }

    @Operation(summary = "Receive an inventory purchase",
            description = "Atomically marks the purchase as RECEIVED, updates consumable stock, and logs auditable ENTRY movements in Kardex.")
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<PurchaseResponse> receive(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(purchaseService.receive(id, principal.userId()));
    }
}
