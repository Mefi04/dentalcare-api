package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.response.PurchaseItemResponse;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.Purchase;
import com.dentalcare.api.modules.inventory.model.PurchaseItem;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseMapperTests {

    private final PurchaseMapper mapper = new PurchaseMapper();

    @Test
    void toResponseCalculatesTotalsAndMapsItems() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        User creator = new User(UUID.randomUUID(), "creator", "Creator User", "creator@mail.com",
                "1000000000001", "hash", UserStatus.ACTIVE, now, now);
        User receiver = new User(UUID.randomUUID(), "receiver", "Receiver User", "receiver@mail.com",
                "1000000000002", "hash", UserStatus.ACTIVE, now, now);
        Supplier supplier = new Supplier(UUID.randomUUID(), "Dental Corp", "Contact", "12345",
                "sup@mail.com", "Address", "Notes", SupplierStatus.ACTIVE, now, now);

        InventoryItem item1 = new InventoryItem(UUID.randomUUID(), "CON-001", "Guantes Latex",
                "Caja 100", InventoryItemType.CONSUMABLE, "Proteccion", InventoryItemStatus.ACTIVE,
                "caja", 10, 5, LocalDate.parse("2027-01-01"), null, null, null, now, now);
        InventoryItem item2 = new InventoryItem(UUID.randomUUID(), "CON-002", "Mascarillas",
                "Caja 50", InventoryItemType.CONSUMABLE, "Proteccion", InventoryItemStatus.ACTIVE,
                "caja", 20, 10, LocalDate.parse("2027-01-01"), null, null, null, now, now);

        Purchase purchase = new Purchase(
                UUID.randomUUID(), "PUR-2026-000001", supplier, PurchaseStatus.RECEIVED,
                LocalDate.parse("2026-10-01"), "REF-100", "Obs", creator, now
        );
        purchase.setReceivedBy(receiver);
        purchase.setReceivedAt(now.plusSeconds(3600));

        PurchaseItem pItem1 = new PurchaseItem(UUID.randomUUID(), purchase, item1, 5, new BigDecimal("45.50"));
        PurchaseItem pItem2 = new PurchaseItem(UUID.randomUUID(), purchase, item2, 10, new BigDecimal("25.00"));
        purchase.addItem(pItem1);
        purchase.addItem(pItem2);

        PurchaseResponse response = mapper.toResponse(purchase);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(purchase.getId());
        assertThat(response.code()).isEqualTo("PUR-2026-000001");
        assertThat(response.supplierId()).isEqualTo(supplier.getId());
        assertThat(response.supplierName()).isEqualTo("Dental Corp");
        assertThat(response.status()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(response.purchaseDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(response.createdBy()).isEqualTo(creator.getId());
        assertThat(response.createdByName()).isEqualTo("Creator User");
        assertThat(response.receivedBy()).isEqualTo(receiver.getId());
        assertThat(response.receivedByName()).isEqualTo("Receiver User");
        assertThat(response.receivedAt()).isEqualTo(now.plusSeconds(3600));

        // Total: 5 * 45.50 (227.50) + 10 * 25.00 (250.00) = 477.50
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("477.50"));
        assertThat(response.items()).hasSize(2);

        PurchaseItemResponse itemRes1 = response.items().get(0);
        assertThat(itemRes1.inventoryItemId()).isEqualTo(item1.getId());
        assertThat(itemRes1.consumableId()).isEqualTo(item1.getId());
        assertThat(itemRes1.itemCode()).isEqualTo("CON-001");
        assertThat(itemRes1.itemName()).isEqualTo("Guantes Latex");
        assertThat(itemRes1.quantity()).isEqualTo(5);
        assertThat(itemRes1.unitCost()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(itemRes1.subtotal()).isEqualByComparingTo(new BigDecimal("227.50"));
    }
}
