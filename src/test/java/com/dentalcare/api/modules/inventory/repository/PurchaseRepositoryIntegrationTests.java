package com.dentalcare.api.modules.inventory.repository;

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
import com.dentalcare.api.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PurchaseRepositoryIntegrationTests {

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
    private PurchaseRepository purchaseRepository;
    @Autowired
    private PurchaseItemRepository purchaseItemRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private User operator;
    private Supplier supplier;
    private InventoryItem consumable;

    @BeforeEach
    void setUp() {
        operator = userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "purchase-op-" + UUID.randomUUID(), "Operator",
                "op-" + UUID.randomUUID() + "@mail.com", "1000000000099", "hash",
                UserStatus.ACTIVE, NOW, NOW
        ));

        supplier = supplierRepository.saveAndFlush(new Supplier(
                UUID.randomUUID(), "Dent-Vendor-" + UUID.randomUUID(), "Vendor", "12345",
                "v@mail.com", "Guatemala", "Notes", SupplierStatus.ACTIVE, NOW, NOW
        ));

        consumable = inventoryItemRepository.saveAndFlush(new InventoryItem(
                UUID.randomUUID(), "CON-" + UUID.randomUUID(), "Guantes Nitrilo", "Desc",
                InventoryItemType.CONSUMABLE, "Proteccion", InventoryItemStatus.ACTIVE,
                "caja", 10, 5, LocalDate.parse("2027-01-01"), null, null, null, NOW, NOW
        ));
    }

    @Test
    void getNextPurchaseCodeSeqGeneratesMonotonicValues() {
        Long first = purchaseRepository.getNextPurchaseCodeSeq();
        Long second = purchaseRepository.getNextPurchaseCodeSeq();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void saveAndFindDetailById() {
        Purchase purchase = new Purchase(
                UUID.randomUUID(), "PUR-2026-000001", supplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), "REF-100", "Obs", operator, NOW
        );
        PurchaseItem item = new PurchaseItem(
                UUID.randomUUID(), purchase, consumable, 10, new BigDecimal("45.00")
        );
        purchase.addItem(item);

        purchaseRepository.saveAndFlush(purchase);
        entityManager.clear();

        Purchase found = purchaseRepository.findDetailById(purchase.getId()).orElseThrow();
        assertThat(found.getCode()).isEqualTo("PUR-2026-000001");
        assertThat(found.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getQuantity()).isEqualTo(10);
        assertThat(found.getItems().get(0).getUnitCost()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    void uniqueCodeConstraintPreventsDuplicates() {
        Purchase p1 = new Purchase(
                UUID.randomUUID(), "PUR-DUP-01", supplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), null, null, operator, NOW
        );
        purchaseRepository.saveAndFlush(p1);

        Purchase p2 = new Purchase(
                UUID.randomUUID(), "PUR-DUP-01", supplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), null, null, operator, NOW
        );

        assertThatThrownBy(() -> purchaseRepository.saveAndFlush(p2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniquePurchaseItemConstraintPreventsDuplicateItemInSamePurchase() {
        Purchase purchase = new Purchase(
                UUID.randomUUID(), "PUR-ITEM-DUP", supplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), null, null, operator, NOW
        );
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable, 5, new BigDecimal("10.00")));
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable, 3, new BigDecimal("10.00")));

        assertThatThrownBy(() -> purchaseRepository.saveAndFlush(purchase))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptConstraintEnforcesConsistency() {
        // RECEIVED status without received_at and received_by must fail CHECK constraint
        Purchase purchase = new Purchase(
                UUID.randomUUID(), "PUR-CHECK-01", supplier, PurchaseStatus.RECEIVED,
                LocalDate.parse("2026-10-01"), null, null, operator, NOW
        );

        assertThatThrownBy(() -> purchaseRepository.saveAndFlush(purchase))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdForUpdateLocksRow() {
        Purchase purchase = new Purchase(
                UUID.randomUUID(), "PUR-LOCK-01", supplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), null, null, operator, NOW
        );
        purchaseRepository.saveAndFlush(purchase);
        entityManager.clear();

        Optional<Purchase> locked = purchaseRepository.findByIdForUpdate(purchase.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(purchase.getId());
    }
}
