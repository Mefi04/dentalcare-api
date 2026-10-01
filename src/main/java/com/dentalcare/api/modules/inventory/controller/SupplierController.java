package com.dentalcare.api.modules.inventory.controller;

import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/suppliers")
@Tag(name = "Inventory Suppliers", description = "Management of inventory suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(summary = "List and search suppliers",
            description = "Supports filtering by name query and active/inactive status with pagination.")
    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<Page<SupplierResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(supplierService.findAll(search, status, page, size));
    }

    @Operation(summary = "Get a supplier by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<SupplierResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.findById(id));
    }

    @Operation(summary = "Create a new supplier")
    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.create(request));
    }

    @Operation(summary = "Update supplier details")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<SupplierResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }

    @Operation(summary = "Update supplier status")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    public ResponseEntity<SupplierResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierStatusRequest request) {
        return ResponseEntity.ok(supplierService.updateStatus(id, request));
    }
}
