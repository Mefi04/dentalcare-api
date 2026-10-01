package com.dentalcare.api.modules.inventory.repository;

import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InventoryItemRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindConsumable() {
        UUID id = UUID.randomUUID();
        InventoryItem consumable = new InventoryItem(
                id, "CON-001", "Guantes de nitrilo", "Guantes descartables",
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", 18, 6, LocalDate.of(2027, 8, 31),
                null, null, null, NOW, NOW
        );

        inventoryItemRepository.saveAndFlush(consumable);
        entityManager.clear();

        Optional<InventoryItem> found = inventoryItemRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("CON-001");
        assertThat(found.get().getName()).isEqualTo("Guantes de nitrilo");
        assertThat(found.get().getType()).isEqualTo(InventoryItemType.CONSUMABLE);
        assertThat(found.get().getUnit()).isEqualTo("Caja");
        assertThat(found.get().getCurrentStock()).isEqualTo(18);
        assertThat(found.get().getMinimumStock()).isEqualTo(6);
        assertThat(found.get().getExpirationDate()).isEqualTo(LocalDate.of(2027, 8, 31));
    }

    @Test
    void saveAndFindInstrument() {
        UUID id = UUID.randomUUID();
        InventoryItem instrument = new InventoryItem(
                id, "INS-001", "Espejo dental", "Espejo n° 5",
                InventoryItemType.INSTRUMENT, "Diagnóstico", InventoryItemStatus.ACTIVE,
                null, null, null, null,
                "Gabinete A", 24, 18, NOW, NOW
        );

        inventoryItemRepository.saveAndFlush(instrument);
        entityManager.clear();

        Optional<InventoryItem> found = inventoryItemRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("INS-001");
        assertThat(found.get().getName()).isEqualTo("Espejo dental");
        assertThat(found.get().getType()).isEqualTo(InventoryItemType.INSTRUMENT);
        assertThat(found.get().getLocation()).isEqualTo("Gabinete A");
        assertThat(found.get().getTotalQuantity()).isEqualTo(24);
        assertThat(found.get().getAvailableQuantity()).isEqualTo(18);
    }

    @Test
    void duplicateCodeViolatesUniqueConstraint() {
        InventoryItem item1 = new InventoryItem(
                UUID.randomUUID(), "DUP-001", "Item 1", null,
                InventoryItemType.CONSUMABLE, "General", InventoryItemStatus.ACTIVE,
                "Caja", 10, 2, null, null, null, null, NOW, NOW
        );
        inventoryItemRepository.saveAndFlush(item1);

        InventoryItem item2 = new InventoryItem(
                UUID.randomUUID(), "DUP-001", "Item 2", null,
                InventoryItemType.CONSUMABLE, "General", InventoryItemStatus.ACTIVE,
                "Caja", 5, 1, null, null, null, null, NOW, NOW
        );

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(item2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameNameWithDifferentCodeIsPermitted() {
        InventoryItem item1 = new InventoryItem(
                UUID.randomUUID(), "CON-GUA-M", "Guantes de nitrilo", null,
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", 10, 2, null, null, null, null, NOW, NOW
        );
        inventoryItemRepository.saveAndFlush(item1);

        InventoryItem item2 = new InventoryItem(
                UUID.randomUUID(), "CON-GUA-L", "Guantes de nitrilo", null,
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", 15, 3, null, null, null, null, NOW, NOW
        );

        InventoryItem saved = inventoryItemRepository.saveAndFlush(item2);
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void consumableCheckConstraintEnforcedWhenUnitNull() {
        InventoryItem invalidConsumable = new InventoryItem(
                UUID.randomUUID(), "INV-001", "Sin unidad", null,
                InventoryItemType.CONSUMABLE, "General", InventoryItemStatus.ACTIVE,
                null, 10, 2, null, null, null, null, NOW, NOW
        );

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(invalidConsumable))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void instrumentCheckConstraintEnforcedWhenAvailableExceedsTotal() {
        InventoryItem invalidInstrument = new InventoryItem(
                UUID.randomUUID(), "INS-INV", "Instrumento inválido", null,
                InventoryItemType.INSTRUMENT, "Cirugía", InventoryItemStatus.ACTIVE,
                null, null, null, null,
                "Armario", 5, 10, NOW, NOW // available 10 > total 5
        );

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(invalidInstrument))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void specificationsFilterCorrectly() {
        InventoryItem item1 = new InventoryItem(
                UUID.randomUUID(), "CON-SPEC-1", "Gasas estériles", null,
                InventoryItemType.CONSUMABLE, "Curación", InventoryItemStatus.ACTIVE,
                "Paquete", 40, 10, null, null, null, null, NOW, NOW
        );
        InventoryItem item2 = new InventoryItem(
                UUID.randomUUID(), "INS-SPEC-2", "Pinza algodonera", null,
                InventoryItemType.INSTRUMENT, "Operatoria", InventoryItemStatus.ACTIVE,
                null, null, null, null, "Gabinete B", 12, 10, NOW, NOW
        );
        InventoryItem item3 = new InventoryItem(
                UUID.randomUUID(), "CON-SPEC-3", "Anestésico local", null,
                InventoryItemType.CONSUMABLE, "Anestesia", InventoryItemStatus.INACTIVE,
                "Caja", 5, 2, null, null, null, null, NOW, NOW
        );

        inventoryItemRepository.saveAndFlush(item1);
        inventoryItemRepository.saveAndFlush(item2);
        inventoryItemRepository.saveAndFlush(item3);

        Specification<InventoryItem> consumableSpec = (root, query, cb) ->
                cb.equal(root.get("type"), InventoryItemType.CONSUMABLE);
        Page<InventoryItem> consumables = inventoryItemRepository.findAll(consumableSpec, PageRequest.of(0, 10));
        assertThat(consumables.getContent()).extracting(InventoryItem::getCode)
                .contains("CON-SPEC-1", "CON-SPEC-3")
                .doesNotContain("INS-SPEC-2");

        Specification<InventoryItem> activeSpec = (root, query, cb) ->
                cb.equal(root.get("status"), InventoryItemStatus.ACTIVE);
        Page<InventoryItem> activeItems = inventoryItemRepository.findAll(activeSpec, PageRequest.of(0, 10));
        assertThat(activeItems.getContent()).extracting(InventoryItem::getCode)
                .contains("CON-SPEC-1", "INS-SPEC-2")
                .doesNotContain("CON-SPEC-3");
    }
}
