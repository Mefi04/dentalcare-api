package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface PurchaseService {

    Page<PurchaseResponse> findAll(UUID supplierId, PurchaseStatus status,
                                   LocalDate from, LocalDate to,
                                   int page, int size);

    PurchaseResponse findById(UUID id);

    PurchaseResponse create(CreatePurchaseRequest request, UUID authenticatedUserId);

    PurchaseResponse receive(UUID id, UUID authenticatedUserId);
}
