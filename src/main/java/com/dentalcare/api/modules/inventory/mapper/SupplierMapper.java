package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SupplierMapper {

    public Supplier toEntity(UUID id, CreateSupplierRequest request, Instant now) {
        if (request == null) {
            return null;
        }
        return new Supplier(
                id,
                normalize(request.name()),
                normalize(request.contactName()),
                normalize(request.phone()),
                normalize(request.email()),
                normalize(request.address()),
                normalize(request.notes()),
                SupplierStatus.ACTIVE,
                now,
                now
        );
    }

    public void updateEntity(Supplier supplier, UpdateSupplierRequest request, Instant now) {
        if (supplier == null || request == null) {
            return;
        }
        supplier.setName(normalize(request.name()));
        supplier.setContactName(normalize(request.contactName()));
        supplier.setPhone(normalize(request.phone()));
        supplier.setEmail(normalize(request.email()));
        supplier.setAddress(normalize(request.address()));
        supplier.setNotes(normalize(request.notes()));
        supplier.setUpdatedAt(now);
    }

    public SupplierResponse toResponse(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getNotes(),
                supplier.getStatus(),
                supplier.getStatus() == SupplierStatus.ACTIVE,
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
