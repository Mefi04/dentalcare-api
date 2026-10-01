package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.mapper.InventoryMovementMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovement;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.inventory.repository.InventoryMovementRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
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
@Import({InventoryMovementServiceImpl.class, InventoryMovementMapper.class,
        InventoryMovementServiceIntegrationTests.ClockConfiguration.class})
class InventoryMovementServiceIntegrationTests {

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
    private InventoryMovementService movementService;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanMovements() {
        movementRepository.deleteAll();
    }

    @Test
    void concurrentExitsLockItemAndNeverProduceNegativeStock() throws Exception {
        User user = createUser("inventory-concurrent", "1000000000101");
        InventoryItem item = createConsumable("CON-CONCURRENT", 5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            results.add(executor.submit(() -> attemptExit(start, item.getId(), user.getId(), 3)));
            results.add(executor.submit(() -> attemptExit(start, item.getId(), user.getId(), 4)));
            start.countDown();

            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertThat(accepted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        InventoryItem reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStock()).isIn(1, 2);
        assertThat(reloaded.getCurrentStock()).isNotNegative();
        assertThat(movementRepository.count()).isEqualTo(1);
    }

    @Test
    void failedMovementInsertRollsBackStockUpdate() {
        User user = createUser("inventory-rollback", "1000000000102");
        InventoryItem item = createConsumable("CON-ROLLBACK", 10);
        String tooLongObservation = "x".repeat(501);

        assertThatThrownBy(() -> movementService.register(item.getId(),
                new CreateInventoryMovementRequest(
                        InventoryMovementType.ENTRY, 5, tooLongObservation, null),
                user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getCurrentStock()).isEqualTo(10);
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void persistsResponsibleUserAndImmutableAuditSnapshot() {
        User user = createUser("inventory-audit", "1000000000104");
        InventoryItem item = createConsumable("CON-AUDIT", 10);

        InventoryMovementResponse response = movementService.register(item.getId(),
                request(InventoryMovementType.ENTRY, 5), user.getId());

        InventoryMovement stored = movementRepository.findById(response.id()).orElseThrow();
        assertThat(stored.getItem().getId()).isEqualTo(item.getId());
        assertThat(stored.getPerformedBy().getId()).isEqualTo(user.getId());
        assertThat(stored.getStockBefore()).isEqualTo(10);
        assertThat(stored.getQuantity()).isEqualTo(5);
        assertThat(stored.getStockAfter()).isEqualTo(15);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void databaseRejectsNonPositiveMovementQuantity() {
        User user = createUser("inventory-check", "1000000000105");
        InventoryItem item = createConsumable("CON-CHECK", 10);
        InventoryMovement invalid = new InventoryMovement(
                UUID.randomUUID(), item, InventoryMovementType.ENTRY, 0,
                10, 10, null, null, user, "Invalid", null, NOW);

        assertThatThrownBy(() -> movementRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void kardexFiltersAndPaginatesInTheDatabase() {
        User user = createUser("inventory-kardex", "1000000000103");
        InventoryItem first = createConsumable("CON-KARDEX-1", 10);
        InventoryItem second = createConsumable("CON-KARDEX-2", 20);

        movementService.register(first.getId(), request(InventoryMovementType.ENTRY, 2), user.getId());
        movementService.register(first.getId(), request(InventoryMovementType.EXIT, 1), user.getId());
        movementService.register(second.getId(), request(InventoryMovementType.ENTRY, 4), user.getId());

        Page<InventoryMovementResponse> firstItem = movementService.findByItem(
                first.getId(), null, null, null, null, 0, 1);
        assertThat(firstItem.getTotalElements()).isEqualTo(2);
        assertThat(firstItem.getContent()).hasSize(1);

        Page<InventoryMovementResponse> combinedFilters = movementService.findAll(
                first.getId(), InventoryMovementType.ENTRY, user.getId(), NOW, NOW, 0, 20);
        assertThat(combinedFilters.getContent()).singleElement().satisfies(movement -> {
            assertThat(movement.itemId()).isEqualTo(first.getId());
            assertThat(movement.type()).isEqualTo(InventoryMovementType.ENTRY);
            assertThat(movement.performedById()).isEqualTo(user.getId());
        });

        assertThat(movementService.findAll(
                null, InventoryMovementType.EXIT, null, null, null, 0, 20).getTotalElements())
                .isEqualTo(1);
    }

    private boolean attemptExit(CountDownLatch start, UUID itemId, UUID userId, int quantity)
            throws InterruptedException {
        start.await();
        try {
            movementService.register(itemId, request(InventoryMovementType.EXIT, quantity), userId);
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private CreateInventoryMovementRequest request(InventoryMovementType type, int quantity) {
        return new CreateInventoryMovementRequest(type, quantity, "Test movement", "TEST-REF");
    }

    private User createUser(String username, String cui) {
        return userRepository.saveAndFlush(new User(
                UUID.randomUUID(), username, "Inventory Operator", username + "@example.test",
                cui, "hash", UserStatus.ACTIVE, NOW, NOW));
    }

    private InventoryItem createConsumable(String code, int stock) {
        return itemRepository.saveAndFlush(new InventoryItem(
                UUID.randomUUID(), code, "Consumable " + code, null,
                InventoryItemType.CONSUMABLE, "General", InventoryItemStatus.ACTIVE,
                "Unit", stock, 2, null, null, null, null, NOW, NOW));
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, java.time.ZoneOffset.UTC);
        }
    }
}
