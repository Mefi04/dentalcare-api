package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SupplierService {

    Page<SupplierResponse> findAll(String search, SupplierStatus status, int page, int size);

    SupplierResponse findById(UUID id);

    SupplierResponse create(CreateSupplierRequest request);

    SupplierResponse update(UUID id, UpdateSupplierRequest request);

    SupplierResponse updateStatus(UUID id, UpdateSupplierStatusRequest request);
}
