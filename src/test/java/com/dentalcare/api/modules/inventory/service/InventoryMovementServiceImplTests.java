package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryMovementRepository movementRepository;
    @Mock
    private UserRepository userRepository;

    private InventoryMovementService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new InventoryMovementServiceImpl(
                itemRepository, movementRepository, userRepository,
                new InventoryMovementMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User(UUID.randomUUID(), "inventory-user", "Inventory User",
                "inventory@example.test", "1000000000001", "hash",
                UserStatus.ACTIVE, NOW, NOW);
        lenient().when(movementRepository.saveAndFlush(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void entryIncreasesConsumableStockAndCreatesAuditableMovement() {
        InventoryItem item = consumable(10);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.ENTRY, 5), user.getId());

        assertThat(item.getCurrentStock()).isEqualTo(15);
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.stockBefore()).isEqualTo(10);
        assertThat(response.stockAfter()).isEqualTo(15);
        assertThat(response.performedById()).isEqualTo(user.getId());
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(itemRepository).save(item);
        verify(movementRepository).saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void exitDecreasesConsumableStock() {
        InventoryItem item = consumable(10);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.EXIT, 4), user.getId());

        assertThat(item.getCurrentStock()).isEqualTo(6);
        assertThat(response.stockBefore()).isEqualTo(10);
        assertThat(response.stockAfter()).isEqualTo(6);
    }

    @Test
    void insufficientConsumableStockRejectsEntireOperation() {
        InventoryItem item = consumable(3);
        prepare(item);

        assertThatThrownBy(() -> service.register(item.getId(),
                request(InventoryMovementType.EXIT, 5), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Insufficient inventory stock");

        assertThat(item.getCurrentStock()).isEqualTo(3);
        verify(itemRepository, never()).save(any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void consumableAdjustmentUsesAbsoluteTargetAndPersistsActualMagnitude() {
        InventoryItem item = consumable(10);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.ADJUSTMENT, 6), user.getId());

        assertThat(item.getCurrentStock()).isEqualTo(6);
        assertThat(response.quantity()).isEqualTo(4);
        assertThat(response.stockBefore()).isEqualTo(10);
        assertThat(response.stockAfter()).isEqualTo(6);
    }

    @Test
    void instrumentEntryIncreasesTotalAndAvailableQuantities() {
        InventoryItem item = instrument(10, 7);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.ENTRY, 3), user.getId());

        assertThat(item.getTotalQuantity()).isEqualTo(13);
        assertThat(item.getAvailableQuantity()).isEqualTo(10);
        assertThat(response.availableBefore()).isEqualTo(7);
        assertThat(response.availableAfter()).isEqualTo(10);
    }

    @Test
    void instrumentExitOnlyRemovesAvailableUnits() {
        InventoryItem item = instrument(10, 4);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.EXIT, 3), user.getId());

        assertThat(item.getTotalQuantity()).isEqualTo(7);
        assertThat(item.getAvailableQuantity()).isEqualTo(1);
        assertThat(response.stockAfter()).isEqualTo(7);
        assertThat(response.availableAfter()).isEqualTo(1);

        assertThatThrownBy(() -> service.register(item.getId(),
                request(InventoryMovementType.EXIT, 2), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Insufficient available instrument quantity");
    }

    @Test
    void instrumentAdjustmentPreservesUnavailableUnits() {
        InventoryItem item = instrument(10, 7);
        prepare(item);

        InventoryMovementResponse response = service.register(item.getId(),
                request(InventoryMovementType.ADJUSTMENT, 8), user.getId());

        assertThat(item.getTotalQuantity()).isEqualTo(8);
        assertThat(item.getAvailableQuantity()).isEqualTo(5);
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.availableBefore()).isEqualTo(7);
        assertThat(response.availableAfter()).isEqualTo(5);
    }

    @Test
    void instrumentAdjustmentCannotRemoveUnavailableUnits() {
        InventoryItem item = instrument(10, 2);
        prepare(item);

        assertThatThrownBy(() -> service.register(item.getId(),
                request(InventoryMovementType.ADJUSTMENT, 7), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Adjustment target cannot be lower than the unavailable instrument quantity");

        assertThat(item.getTotalQuantity()).isEqualTo(10);
        assertThat(item.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void inactiveItemRejectsNewMovements() {
        InventoryItem item = consumable(10);
        item.setStatus(InventoryItemStatus.INACTIVE);
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.register(item.getId(),
                request(InventoryMovementType.ENTRY, 1), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Movements cannot be registered for an inactive inventory item");

        verify(userRepository, never()).findById(any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void validatesAdjustmentAndKardexRange() {
        InventoryItem item = consumable(10);
        prepare(item);

        assertThatThrownBy(() -> service.register(item.getId(),
                request(InventoryMovementType.ADJUSTMENT, 10), user.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Adjustment target must differ from current stock");

        assertThatThrownBy(() -> service.findAll(null, null, null,
                NOW.plusSeconds(1), NOW, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("From date must not be after to date");
    }

    @Test
    void adjustmentRequiresAnObservation() {
        InventoryItem item = consumable(10);

        assertThatThrownBy(() -> service.register(item.getId(),
                new CreateInventoryMovementRequest(
                        InventoryMovementType.ADJUSTMENT, 8, " ", null), user.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Observation is required for inventory adjustments");

        verify(itemRepository, never()).findByIdForUpdate(any());
    }

    private void prepare(InventoryItem item) {
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private CreateInventoryMovementRequest request(InventoryMovementType type, int quantity) {
        return new CreateInventoryMovementRequest(type, quantity, "Conteo físico", "REF-001");
    }

    private InventoryItem consumable(int stock) {
        return new InventoryItem(
                UUID.randomUUID(), "CON-001", "Guantes", null,
                InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE,
                "Caja", stock, 2, null, null, null, null, NOW, NOW);
    }

    private InventoryItem instrument(int total, int available) {
        return new InventoryItem(
                UUID.randomUUID(), "INS-001", "Espejo", null,
                InventoryItemType.INSTRUMENT, "Diagnóstico", InventoryItemStatus.ACTIVE,
                null, null, null, null, "Gabinete", total, available, NOW, NOW);
    }
}
