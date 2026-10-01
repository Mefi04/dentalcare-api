package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.response.PurchaseItemResponse;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.model.Purchase;
import com.dentalcare.api.modules.inventory.model.PurchaseItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class PurchaseMapper {

    public PurchaseResponse toResponse(Purchase purchase) {
        if (purchase == null) {
            return null;
        }

        List<PurchaseItemResponse> itemResponses = purchase.getItems() == null
                ? Collections.emptyList()
                : purchase.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal totalAmount = itemResponses.stream()
                .map(PurchaseItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getCode(),
                purchase.getSupplier().getId(),
                purchase.getSupplier().getName(),
                purchase.getStatus(),
                purchase.getPurchaseDate(),
                purchase.getReference(),
                purchase.getObservation(),
                totalAmount,
                purchase.getCreatedBy().getId(),
                purchase.getCreatedBy().getFullName(),
                purchase.getCreatedAt(),
                purchase.getReceivedBy() != null ? purchase.getReceivedBy().getId() : null,
                purchase.getReceivedBy() != null ? purchase.getReceivedBy().getFullName() : null,
                purchase.getReceivedAt(),
                itemResponses
        );
    }

    public PurchaseItemResponse toItemResponse(PurchaseItem item) {
        if (item == null) {
            return null;
        }
        BigDecimal subtotal = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new PurchaseItemResponse(
                item.getId(),
                item.getInventoryItem().getId(),
                item.getInventoryItem().getId(),
                item.getInventoryItem().getCode(),
                item.getInventoryItem().getName(),
                item.getInventoryItem().getUnit(),
                item.getQuantity(),
                item.getUnitCost(),
                subtotal
        );
    }
}
