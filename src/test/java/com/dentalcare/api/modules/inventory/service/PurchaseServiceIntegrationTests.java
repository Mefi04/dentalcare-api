package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.mapper.InventoryMovementMapper;
import com.dentalcare.api.modules.inventory.mapper.PurchaseMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovement;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.model.Purchase;
import com.dentalcare.api.modules.inventory.model.PurchaseItem;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.inventory.repository.InventoryMovementRepository;
import com.dentalcare.api.modules.inventory.repository.PurchaseItemRepository;
import com.dentalcare.api.modules.inventory.repository.PurchaseRepository;
import com.dentalcare.api.modules.inventory.repository.SupplierRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PurchaseServiceImpl.class, PurchaseMapper.class,
        InventoryMovementServiceImpl.class, InventoryMovementMapper.class,
        PurchaseServiceIntegrationTests.ClockConfiguration.class})
class PurchaseServiceIntegrationTests {

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
    private PurchaseService purchaseService;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private PurchaseItemRepository purchaseItemRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanState() {
        purchaseItemRepository.deleteAll();
        purchaseRepository.deleteAll();
        movementRepository.deleteAll();
    }

    @Test
    void receiveSingleItemUpdatesStockAndCreatesEntryMovement() {
        User user = createUser("user-receive-single");
        Supplier supplier = createSupplier("Supplier Single");
        InventoryItem item = createConsumable("CON-SINGLE", 10);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-TEST-SINGLE", supplier.getId(), LocalDate.parse("2026-10-01"), "REF-S", "Obs",
                List.of(new CreatePurchaseItemRequest(item.getId(), null, 5, new BigDecimal("15.00")))
        );

        PurchaseResponse created = purchaseService.create(request, user.getId());
        assertThat(created.status()).isEqualTo(PurchaseStatus.PENDING);

        // Before receiving: stock is still 10, 0 movements
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(10);
        assertThat(movementRepository.findAll()).isEmpty();

        // Receive purchase
        PurchaseResponse received = purchaseService.receive(created.id(), user.getId());
        assertThat(received.status()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(received.receivedBy()).isEqualTo(user.getId());
        assertThat(received.receivedAt()).isNotNull();

        // After receiving: stock is now 15
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(15);

        // 1 ENTRY movement created
        List<InventoryMovement> movements = movementRepository.findAll();
        assertThat(movements).hasSize(1);
        InventoryMovement movement = movements.get(0);
        assertThat(movement.getType()).isEqualTo(InventoryMovementType.ENTRY);
        assertThat(movement.getQuantity()).isEqualTo(5);
        assertThat(movement.getStockBefore()).isEqualTo(10);
        assertThat(movement.getStockAfter()).isEqualTo(15);
        assertThat(movement.getReference()).isEqualTo("PUR-TEST-SINGLE");
        assertThat(movement.getPerformedBy().getId()).isEqualTo(user.getId());
    }

    @Test
    void receiveMultiItemUpdatesAllStocksAndCreatesMultipleEntryMovements() {
        User user = createUser("user-receive-multi");
        Supplier supplier = createSupplier("Supplier Multi");
        InventoryItem itemA = createConsumable("CON-MULTI-A", 10);
        InventoryItem itemB = createConsumable("CON-MULTI-B", 20);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-TEST-MULTI", supplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(
                        new CreatePurchaseItemRequest(itemA.getId(), null, 5, new BigDecimal("10.00")),
                        new CreatePurchaseItemRequest(itemB.getId(), null, 8, new BigDecimal("25.00"))
                )
        );

        PurchaseResponse created = purchaseService.create(request, user.getId());

        PurchaseResponse received = purchaseService.receive(created.id(), user.getId());
        assertThat(received.status()).isEqualTo(PurchaseStatus.RECEIVED);

        assertThat(itemRepository.findById(itemA.getId()).orElseThrow().getCurrentStock()).isEqualTo(15);
        assertThat(itemRepository.findById(itemB.getId()).orElseThrow().getCurrentStock()).isEqualTo(28);

        List<InventoryMovement> movements = movementRepository.findAll();
        assertThat(movements).hasSize(2);
    }

    @Test
    void duplicateReceiveThrowsConflictAndNeverModifiesStockAgain() {
        User user = createUser("user-dup-receive");
        Supplier supplier = createSupplier("Supplier Dup");
        InventoryItem item = createConsumable("CON-DUP", 10);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-TEST-DUP", supplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(new CreatePurchaseItemRequest(item.getId(), null, 5, new BigDecimal("12.00")))
        );

        PurchaseResponse created = purchaseService.create(request, user.getId());
        purchaseService.receive(created.id(), user.getId());

        Instant firstReceivedAt = purchaseRepository.findById(created.id()).orElseThrow().getReceivedAt();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(15);
        assertThat(movementRepository.findAll()).hasSize(1);

        // Second receive must be rejected with ConflictException
        assertThatThrownBy(() -> purchaseService.receive(created.id(), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been received");

        // Stock must still be 15, no extra movements
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(15);
        assertThat(movementRepository.findAll()).hasSize(1);
        assertThat(purchaseRepository.findById(created.id()).orElseThrow().getReceivedAt()).isEqualTo(firstReceivedAt);
    }

    @Test
    void rollbackEnsuresAtomicFailureWhenOneItemIsInactive() {
        User user = createUser("user-rollback");
        Supplier supplier = createSupplier("Supplier Rollback");
        InventoryItem itemValid = createConsumable("CON-RB-VALID", 10);
        InventoryItem itemInactive = createConsumable("CON-RB-INACT", 20);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-TEST-ROLLBACK", supplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(
                        new CreatePurchaseItemRequest(itemValid.getId(), null, 5, new BigDecimal("10.00")),
                        new CreatePurchaseItemRequest(itemInactive.getId(), null, 8, new BigDecimal("10.00"))
                )
        );

        PurchaseResponse created = purchaseService.create(request, user.getId());

        // Inactivate the second item before reception
        itemInactive.setStatus(InventoryItemStatus.INACTIVE);
        itemRepository.save(itemInactive);

        // Attempt receive: must fail with ConflictException
        assertThatThrownBy(() -> purchaseService.receive(created.id(), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("inactive inventory item");

        // Purchase must remain PENDING
        Purchase reloaded = purchaseRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(reloaded.getReceivedAt()).isNull();

        // Stock of itemValid must NOT have changed (remains 10)
        assertThat(itemRepository.findById(itemValid.getId()).orElseThrow().getCurrentStock()).isEqualTo(10);

        // Zero movements should exist
        assertThat(movementRepository.findAll()).isEmpty();
    }

    @Test
    void concurrentReceivesAllowOnlyOneSuccessAndRejectTheOther() throws Exception {
        User user = createUser("user-concurrent");
        Supplier supplier = createSupplier("Supplier Concurrency");
        InventoryItem item = createConsumable("CON-CONCURRENT-PUR", 10);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-TEST-CONCURRENT", supplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(new CreatePurchaseItemRequest(item.getId(), null, 5, new BigDecimal("10.00")))
        );
        PurchaseResponse created = purchaseService.create(request, user.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            results.add(executor.submit(() -> attemptReceive(startSignal, created.id(), user.getId())));
            results.add(executor.submit(() -> attemptReceive(startSignal, created.id(), user.getId())));
            startSignal.countDown();

            long successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            // Exactly 1 success, 1 failure
            assertThat(successes).isEqualTo(1);

            // Stock increased exactly once: 10 + 5 = 15
            assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(15);

            // Exactly 1 movement created
            assertThat(movementRepository.findAll()).hasSize(1);

            // Purchase is received
            assertThat(purchaseRepository.findById(created.id()).orElseThrow().getStatus())
                    .isEqualTo(PurchaseStatus.RECEIVED);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptReceive(CountDownLatch start, UUID purchaseId, UUID userId) {
        try {
            start.await(10, TimeUnit.SECONDS);
            purchaseService.receive(purchaseId, userId);
            return true;
        } catch (ConflictException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong CUI_COUNTER =
            new java.util.concurrent.atomic.AtomicLong(2000000000000L);

    private User createUser(String prefix) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String cui = String.valueOf(CUI_COUNTER.incrementAndGet());
        return userRepository.save(new User(
                UUID.randomUUID(), prefix + "-" + unique, "Full Name",
                prefix + "-" + unique + "@mail.com", cui,
                "hash", UserStatus.ACTIVE, NOW, NOW
        ));
    }

    private Supplier createSupplier(String name) {
        return supplierRepository.save(new Supplier(
                UUID.randomUUID(), name + " " + UUID.randomUUID(), "Contact", "12345",
                "sup@mail.com", "Zone 10", "Notes", SupplierStatus.ACTIVE, NOW, NOW
        ));
    }

    private InventoryItem createConsumable(String codePrefix, int initialStock) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return itemRepository.save(new InventoryItem(
                UUID.randomUUID(), codePrefix + "-" + unique, "Consumable Item", "Desc",
                InventoryItemType.CONSUMABLE, "General", InventoryItemStatus.ACTIVE,
                "caja", initialStock, 5, LocalDate.parse("2027-01-01"), null, null, null, NOW, NOW
        ));
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        public Clock clock() {
            return Clock.fixed(NOW, ZoneId.of("UTC"));
        }
    }
}
