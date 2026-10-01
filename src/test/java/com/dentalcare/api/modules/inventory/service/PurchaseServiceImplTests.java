package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryMovementRequest;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.CreatePurchaseRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryMovementResponse;
import com.dentalcare.api.modules.inventory.dto.response.PurchaseResponse;
import com.dentalcare.api.modules.inventory.mapper.PurchaseMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.model.InventoryMovementType;
import com.dentalcare.api.modules.inventory.model.Purchase;
import com.dentalcare.api.modules.inventory.model.PurchaseItem;
import com.dentalcare.api.modules.inventory.model.PurchaseStatus;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.inventory.repository.PurchaseRepository;
import com.dentalcare.api.modules.inventory.repository.SupplierRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryMovementService inventoryMovementService;

    private final PurchaseMapper purchaseMapper = new PurchaseMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private PurchaseService purchaseService;

    private User creatorUser;
    private User receiverUser;
    private Supplier activeSupplier;
    private InventoryItem consumable1;
    private InventoryItem consumable2;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseServiceImpl(
                purchaseRepository, supplierRepository, inventoryItemRepository,
                userRepository, inventoryMovementService, purchaseMapper, clock
        );

        creatorUser = new User(UUID.randomUUID(), "creator", "Creator User", "creator@mail.com",
                "1000000000001", "hash", UserStatus.ACTIVE, NOW, NOW);
        receiverUser = new User(UUID.randomUUID(), "receiver", "Receiver User", "receiver@mail.com",
                "1000000000002", "hash", UserStatus.ACTIVE, NOW, NOW);

        activeSupplier = new Supplier(UUID.randomUUID(), "Vendor Inc", "Vendor Contact", "11223344",
                "vendor@mail.com", "Zone 1", "Notes", SupplierStatus.ACTIVE, NOW, NOW);

        consumable1 = new InventoryItem(UUID.randomUUID(), "CON-001", "Anestesia", "Dental",
                InventoryItemType.CONSUMABLE, "Farmacia", InventoryItemStatus.ACTIVE,
                "ampolla", 10, 5, LocalDate.parse("2027-01-01"), null, null, null, NOW, NOW);

        consumable2 = new InventoryItem(UUID.randomUUID(), "CON-002", "Agujas", "Cortas",
                InventoryItemType.CONSUMABLE, "Farmacia", InventoryItemStatus.ACTIVE,
                "caja", 20, 10, LocalDate.parse("2027-01-01"), null, null, null, NOW, NOW);
    }

    @Test
    void createPurchaseSuccessfullyInPendingStatusWithoutStockChanges() {
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                null,
                activeSupplier.getId(),
                LocalDate.parse("2026-10-01"),
                "PO-999",
                "Initial purchase",
                List.of(
                        new CreatePurchaseItemRequest(consumable1.getId(), null, 5, new BigDecimal("12.50")),
                        new CreatePurchaseItemRequest(null, consumable2.getId(), 8, new BigDecimal("7.00"))
                )
        );

        when(userRepository.findById(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(supplierRepository.findById(activeSupplier.getId())).thenReturn(Optional.of(activeSupplier));
        when(purchaseRepository.getNextPurchaseCodeSeq()).thenReturn(42L);
        when(inventoryItemRepository.findById(consumable1.getId())).thenReturn(Optional.of(consumable1));
        when(inventoryItemRepository.findById(consumable2.getId())).thenReturn(Optional.of(consumable2));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseResponse response = purchaseService.create(request, creatorUser.getId());

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("PUR-2026-000042");
        assertThat(response.status()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(response.supplierId()).isEqualTo(activeSupplier.getId());
        assertThat(response.items()).hasSize(2);
        assertThat(response.receivedAt()).isNull();
        assertThat(response.receivedBy()).isNull();

        // Stock was not modified, movement service was NEVER called
        verify(inventoryMovementService, never()).register(any(), any(), any());
    }

    @Test
    void createThrowsConflictWhenSupplierIsInactive() {
        Supplier inactive = new Supplier(UUID.randomUUID(), "Inactive Vendor", "Contact", "1111",
                "mail@mail.com", "Address", "Notes", SupplierStatus.INACTIVE, NOW, NOW);
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-001", inactive.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(new CreatePurchaseItemRequest(consumable1.getId(), null, 5, new BigDecimal("10.00")))
        );

        when(userRepository.findById(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(supplierRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> purchaseService.create(request, creatorUser.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("inactive supplier");
    }

    @Test
    void createThrowsBadRequestWhenDuplicateItemsInRequest() {
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-001", activeSupplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(
                        new CreatePurchaseItemRequest(consumable1.getId(), null, 5, new BigDecimal("10.00")),
                        new CreatePurchaseItemRequest(consumable1.getId(), null, 3, new BigDecimal("10.00"))
                )
        );

        when(userRepository.findById(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(supplierRepository.findById(activeSupplier.getId())).thenReturn(Optional.of(activeSupplier));

        assertThatThrownBy(() -> purchaseService.create(request, creatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate inventory items");
    }

    @Test
    void createThrowsConflictWhenItemIsInactive() {
        consumable1.setStatus(InventoryItemStatus.INACTIVE);
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-001", activeSupplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(new CreatePurchaseItemRequest(consumable1.getId(), null, 5, new BigDecimal("10.00")))
        );

        when(userRepository.findById(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(supplierRepository.findById(activeSupplier.getId())).thenReturn(Optional.of(activeSupplier));
        when(inventoryItemRepository.findById(consumable1.getId())).thenReturn(Optional.of(consumable1));

        assertThatThrownBy(() -> purchaseService.create(request, creatorUser.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("inactive inventory item");
    }

    @Test
    void createThrowsBadRequestWhenItemTypeIsInstrument() {
        InventoryItem instrument = new InventoryItem(UUID.randomUUID(), "INS-001", "Espejo", "Exploracion",
                InventoryItemType.INSTRUMENT, "Diagnostico", InventoryItemStatus.ACTIVE,
                null, null, null, null, "Box 1", 10, 10, NOW, NOW);

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "PUR-001", activeSupplier.getId(), LocalDate.parse("2026-10-01"), null, null,
                List.of(new CreatePurchaseItemRequest(instrument.getId(), null, 5, new BigDecimal("10.00")))
        );

        when(userRepository.findById(creatorUser.getId())).thenReturn(Optional.of(creatorUser));
        when(supplierRepository.findById(activeSupplier.getId())).thenReturn(Optional.of(activeSupplier));
        when(inventoryItemRepository.findById(instrument.getId())).thenReturn(Optional.of(instrument));

        assertThatThrownBy(() -> purchaseService.create(request, creatorUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consumable inventory items");
    }

    @Test
    void receivePurchaseUpdatesStatusAndRegistersKardexMovements() {
        UUID purchaseId = UUID.randomUUID();
        Purchase purchase = new Purchase(
                purchaseId, "PUR-2026-000001", activeSupplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), "REF-1", "Obs", creatorUser, NOW
        );
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable1, 5, new BigDecimal("10.00")));
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable2, 8, new BigDecimal("20.00")));

        when(purchaseRepository.findByIdForUpdate(purchaseId)).thenReturn(Optional.of(purchase));
        when(userRepository.findById(receiverUser.getId())).thenReturn(Optional.of(receiverUser));
        when(purchaseRepository.save(purchase)).thenReturn(purchase);

        PurchaseResponse response = purchaseService.receive(purchaseId, receiverUser.getId());

        assertThat(response.status()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(response.receivedBy()).isEqualTo(receiverUser.getId());
        assertThat(response.receivedByName()).isEqualTo(receiverUser.getFullName());
        assertThat(response.receivedAt()).isEqualTo(NOW);

        // Verify ENTRY movements were registered via InventoryMovementService
        ArgumentCaptor<CreateInventoryMovementRequest> captor = ArgumentCaptor.forClass(CreateInventoryMovementRequest.class);
        verify(inventoryMovementService, times(2)).register(any(UUID.class), captor.capture(), eq(receiverUser.getId()));

        List<CreateInventoryMovementRequest> requests = captor.getAllValues();
        assertThat(requests.get(0).type()).isEqualTo(InventoryMovementType.ENTRY);
        assertThat(requests.get(0).quantity()).isEqualTo(5);
        assertThat(requests.get(0).reference()).isEqualTo("PUR-2026-000001");

        assertThat(requests.get(1).type()).isEqualTo(InventoryMovementType.ENTRY);
        assertThat(requests.get(1).quantity()).isEqualTo(8);
        assertThat(requests.get(1).reference()).isEqualTo("PUR-2026-000001");
    }

    @Test
    void receiveDuplicatePurchaseThrowsConflictAndNeverCallsMovementServiceAgain() {
        UUID purchaseId = UUID.randomUUID();
        Purchase purchase = new Purchase(
                purchaseId, "PUR-2026-000001", activeSupplier, PurchaseStatus.RECEIVED,
                LocalDate.parse("2026-10-01"), "REF-1", "Obs", creatorUser, NOW
        );
        purchase.setReceivedBy(receiverUser);
        purchase.setReceivedAt(NOW);

        when(purchaseRepository.findByIdForUpdate(purchaseId)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.receive(purchaseId, receiverUser.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been received");

        verify(inventoryMovementService, never()).register(any(), any(), any());
    }

    @Test
    void receiveRollsBackWhenMovementServiceThrowsException() {
        UUID purchaseId = UUID.randomUUID();
        Purchase purchase = new Purchase(
                purchaseId, "PUR-2026-000001", activeSupplier, PurchaseStatus.PENDING,
                LocalDate.parse("2026-10-01"), "REF-1", "Obs", creatorUser, NOW
        );
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable1, 5, new BigDecimal("10.00")));
        purchase.addItem(new PurchaseItem(UUID.randomUUID(), purchase, consumable2, 8, new BigDecimal("20.00")));

        when(purchaseRepository.findByIdForUpdate(purchaseId)).thenReturn(Optional.of(purchase));
        when(userRepository.findById(receiverUser.getId())).thenReturn(Optional.of(receiverUser));

        // First item succeeds, second item throws exception (e.g. inactive item conflict)
        when(inventoryMovementService.register(eq(consumable1.getId()), any(), eq(receiverUser.getId())))
                .thenReturn(null);
        when(inventoryMovementService.register(eq(consumable2.getId()), any(), eq(receiverUser.getId())))
                .thenThrow(new ConflictException("Movements cannot be registered for an inactive inventory item"));

        assertThatThrownBy(() -> purchaseService.receive(purchaseId, receiverUser.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("inactive inventory item");

        // Purchase must still be PENDING in object state
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(purchase.getReceivedAt()).isNull();
        verify(purchaseRepository, never()).save(purchase);
    }
}
