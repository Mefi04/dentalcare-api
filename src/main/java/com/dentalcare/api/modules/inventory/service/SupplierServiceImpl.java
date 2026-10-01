package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.mapper.SupplierMapper;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.repository.SupplierRepository;
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
public class SupplierServiceImpl implements SupplierService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("id"));

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final Clock clock;

    public SupplierServiceImpl(SupplierRepository supplierRepository,
                               SupplierMapper supplierMapper,
                               Clock clock) {
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> findAll(String search, SupplierStatus status, int page, int size) {
        validatePaging(page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), DEFAULT_SORT);

        Specification<Supplier> specification = Specification.unrestricted();
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim().toLowerCase();
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + trimmed + "%"));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        return supplierRepository.findAll(specification, pageable)
                .map(supplierMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse findById(UUID id) {
        requireId(id, "Supplier id is required");
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        if (request == null) {
            throw new BadRequestException("Supplier data is required");
        }
        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Supplier name is required");
        }
        if (supplierRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ConflictException("A supplier with this name already exists");
        }

        Instant now = clock.instant();
        Supplier supplier = supplierMapper.toEntity(UUID.randomUUID(), request, now);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest request) {
        requireId(id, "Supplier id is required");
        if (request == null) {
            throw new BadRequestException("Supplier data is required");
        }
        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Supplier name is required");
        }

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));

        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException("A supplier with this name already exists");
        }

        Instant now = clock.instant();
        supplierMapper.updateEntity(supplier, request, now);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(UUID id, UpdateSupplierStatusRequest request) {
        requireId(id, "Supplier id is required");
        if (request == null || request.status() == null) {
            throw new BadRequestException("Supplier status is required");
        }

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));

        supplier.setStatus(request.status());
        supplier.setUpdatedAt(clock.instant());
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    private void validatePaging(int page, int size) {
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
}
