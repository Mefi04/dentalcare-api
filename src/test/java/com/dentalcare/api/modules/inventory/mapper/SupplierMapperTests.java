package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierMapperTests {

    private final SupplierMapper mapper = new SupplierMapper();

    @Test
    void toEntityTrimsWhitespaceAndInitializesActive() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        CreateSupplierRequest request = new CreateSupplierRequest(
                "  Dental Supplies Corp  ",
                "  Carlos Perez  ",
                "  12345678  ",
                "  carlos@dentalsupplies.com  ",
                "  Zone 10  ",
                "  Preferred vendor  "
        );

        Supplier entity = mapper.toEntity(id, request, now);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("Dental Supplies Corp");
        assertThat(entity.getContactName()).isEqualTo("Carlos Perez");
        assertThat(entity.getPhone()).isEqualTo("12345678");
        assertThat(entity.getEmail()).isEqualTo("carlos@dentalsupplies.com");
        assertThat(entity.getAddress()).isEqualTo("Zone 10");
        assertThat(entity.getNotes()).isEqualTo("Preferred vendor");
        assertThat(entity.getStatus()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void updateEntityModifiesFieldsAndSetsUpdatedAt() {
        Instant initial = Instant.parse("2026-10-01T10:00:00Z");
        Instant updated = Instant.parse("2026-10-01T11:00:00Z");
        Supplier supplier = new Supplier(
                UUID.randomUUID(), "Old Name", "Old Contact", "11111111", "old@mail.com",
                "Old Address", "Old Notes", SupplierStatus.ACTIVE, initial, initial
        );

        UpdateSupplierRequest request = new UpdateSupplierRequest(
                "New Name", "New Contact", "22222222", "new@mail.com",
                "New Address", "New Notes"
        );

        mapper.updateEntity(supplier, request, updated);

        assertThat(supplier.getName()).isEqualTo("New Name");
        assertThat(supplier.getContactName()).isEqualTo("New Contact");
        assertThat(supplier.getPhone()).isEqualTo("22222222");
        assertThat(supplier.getEmail()).isEqualTo("new@mail.com");
        assertThat(supplier.getAddress()).isEqualTo("New Address");
        assertThat(supplier.getNotes()).isEqualTo("New Notes");
        assertThat(supplier.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    void toResponseMapsAllFieldsAndComputesActiveFlag() {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        Supplier activeSupplier = new Supplier(
                UUID.randomUUID(), "MedTech", "Ana Gomez", "55556666", "ana@medtech.com",
                "Zone 9", "Biweekly delivery", SupplierStatus.ACTIVE, now, now
        );

        SupplierResponse response = mapper.toResponse(activeSupplier);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(activeSupplier.getId());
        assertThat(response.name()).isEqualTo("MedTech");
        assertThat(response.contactName()).isEqualTo("Ana Gomez");
        assertThat(response.phone()).isEqualTo("55556666");
        assertThat(response.email()).isEqualTo("ana@medtech.com");
        assertThat(response.address()).isEqualTo("Zone 9");
        assertThat(response.notes()).isEqualTo("Biweekly delivery");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(response.active()).isTrue();

        activeSupplier.setStatus(SupplierStatus.INACTIVE);
        SupplierResponse inactiveResponse = mapper.toResponse(activeSupplier);
        assertThat(inactiveResponse.active()).isFalse();
    }
}
