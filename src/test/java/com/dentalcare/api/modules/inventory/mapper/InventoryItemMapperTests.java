package com.dentalcare.api.modules.inventory.mapper;

import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryItemMapperTests {

    private InventoryItemMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InventoryItemMapper();
    }

    @Test
    void toEntityMapsConsumableRequestCorrectly() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "CON-001",
                "Guantes de nitrilo",
                "Caja de guantes talla M",
                InventoryItemType.CONSUMABLE,
                "Protección personal",
                "Caja",
                18,
                6,
                LocalDate.of(2027, 8, 31),
                "Armario quirúrgico", // should be ignored for consumable
                10, // should be ignored for consumable
                5 // should be ignored for consumable
        );

        InventoryItem item = mapper.toEntity(request);

        assertThat(item).isNotNull();
        assertThat(item.getCode()).isEqualTo("CON-001");
        assertThat(item.getName()).isEqualTo("Guantes de nitrilo");
        assertThat(item.getDescription()).isEqualTo("Caja de guantes talla M");
        assertThat(item.getType()).isEqualTo(InventoryItemType.CONSUMABLE);
        assertThat(item.getCategory()).isEqualTo("Protección personal");
        assertThat(item.getStatus()).isEqualTo(InventoryItemStatus.ACTIVE);
        assertThat(item.getUnit()).isEqualTo("Caja");
        assertThat(item.getCurrentStock()).isEqualTo(18);
        assertThat(item.getMinimumStock()).isEqualTo(6);
        assertThat(item.getExpirationDate()).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(item.getLocation()).isNull();
        assertThat(item.getTotalQuantity()).isNull();
        assertThat(item.getAvailableQuantity()).isNull();
    }

    @Test
    void toEntityMapsInstrumentRequestCorrectly() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "INS-001",
                "Espejo dental",
                "Espejo de exploración plano",
                InventoryItemType.INSTRUMENT,
                "Diagnóstico",
                "Unidad", // should be ignored for instrument
                10, // should be ignored for instrument
                2, // should be ignored for instrument
                LocalDate.of(2030, 1, 1), // should be ignored for instrument
                "Gabinete A",
                24,
                18
        );

        InventoryItem item = mapper.toEntity(request);

        assertThat(item).isNotNull();
        assertThat(item.getCode()).isEqualTo("INS-001");
        assertThat(item.getName()).isEqualTo("Espejo dental");
        assertThat(item.getDescription()).isEqualTo("Espejo de exploración plano");
        assertThat(item.getType()).isEqualTo(InventoryItemType.INSTRUMENT);
        assertThat(item.getCategory()).isEqualTo("Diagnóstico");
        assertThat(item.getStatus()).isEqualTo(InventoryItemStatus.ACTIVE);
        assertThat(item.getLocation()).isEqualTo("Gabinete A");
        assertThat(item.getTotalQuantity()).isEqualTo(24);
        assertThat(item.getAvailableQuantity()).isEqualTo(18);
        assertThat(item.getUnit()).isNull();
        assertThat(item.getCurrentStock()).isNull();
        assertThat(item.getMinimumStock()).isNull();
        assertThat(item.getExpirationDate()).isNull();
    }

    @Test
    void updateEntityUpdatesConsumableAttributes() {
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(), "CON-001", "Guantes", "Desc",
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", 10, 5, LocalDate.of(2027, 1, 1),
                null, null, null,
                Instant.now(), Instant.now()
        );

        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest(
                "CON-001-MOD",
                "Guantes de nitrilo azul",
                "Nueva descripción",
                "Bioseguridad",
                InventoryItemStatus.INACTIVE,
                "Paquete",
                10,
                LocalDate.of(2028, 5, 20),
                "No aplica para consumible"
        );

        mapper.updateEntity(item, request);

        assertThat(item.getCode()).isEqualTo("CON-001-MOD");
        assertThat(item.getName()).isEqualTo("Guantes de nitrilo azul");
        assertThat(item.getDescription()).isEqualTo("Nueva descripción");
        assertThat(item.getCategory()).isEqualTo("Bioseguridad");
        assertThat(item.getStatus()).isEqualTo(InventoryItemStatus.INACTIVE);
        assertThat(item.getUnit()).isEqualTo("Paquete");
        assertThat(item.getMinimumStock()).isEqualTo(10);
        assertThat(item.getExpirationDate()).isEqualTo(LocalDate.of(2028, 5, 20));
        assertThat(item.getCurrentStock()).isEqualTo(10); // currentStock not modified
        assertThat(item.getLocation()).isNull(); // instrument field untouched
    }

    @Test
    void updateEntityUpdatesInstrumentAttributes() {
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(), "INS-001", "Espejo", "Desc",
                InventoryItemType.INSTRUMENT, "Diagnóstico", InventoryItemStatus.ACTIVE,
                null, null, null, null,
                "Gabinete A", 20, 15,
                Instant.now(), Instant.now()
        );

        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest(
                "INS-001",
                "Espejo plano n° 5",
                "Nuevo espejo",
                "Examen clínico",
                InventoryItemStatus.ACTIVE,
                "Frasco", // consumable field ignored
                3, // consumable field ignored
                null,
                "Gabinete B"
        );

        mapper.updateEntity(item, request);

        assertThat(item.getName()).isEqualTo("Espejo plano n° 5");
        assertThat(item.getDescription()).isEqualTo("Nuevo espejo");
        assertThat(item.getCategory()).isEqualTo("Examen clínico");
        assertThat(item.getLocation()).isEqualTo("Gabinete B");
        assertThat(item.getTotalQuantity()).isEqualTo(20); // totalQuantity unchanged
        assertThat(item.getAvailableQuantity()).isEqualTo(15); // availableQuantity unchanged
        assertThat(item.getUnit()).isNull();
    }

    @Test
    void toResponseMapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-10-01T11:00:00Z");

        InventoryItem item = new InventoryItem(
                id, "CON-001", "Guantes de nitrilo", "Descripción",
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", 18, 6, LocalDate.of(2027, 8, 31),
                null, null, null,
                createdAt, updatedAt
        );

        InventoryItemResponse response = mapper.toResponse(item);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.code()).isEqualTo("CON-001");
        assertThat(response.name()).isEqualTo("Guantes de nitrilo");
        assertThat(response.description()).isEqualTo("Descripción");
        assertThat(response.type()).isEqualTo(InventoryItemType.CONSUMABLE);
        assertThat(response.category()).isEqualTo("Protección");
        assertThat(response.status()).isEqualTo(InventoryItemStatus.ACTIVE);
        assertThat(response.unit()).isEqualTo("Caja");
        assertThat(response.currentStock()).isEqualTo(18);
        assertThat(response.minimumStock()).isEqualTo(6);
        assertThat(response.expirationDate()).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(response.location()).isNull();
        assertThat(response.totalQuantity()).isNull();
        assertThat(response.availableQuantity()).isNull();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void returnsNullWhenInputsAreNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
