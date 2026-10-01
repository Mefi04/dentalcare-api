package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateInventoryItemRequest;
import com.dentalcare.api.modules.inventory.dto.response.InventoryItemResponse;
import com.dentalcare.api.modules.inventory.mapper.InventoryItemMapper;
import com.dentalcare.api.modules.inventory.model.InventoryItem;
import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import com.dentalcare.api.modules.inventory.model.InventoryItemType;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    private InventoryItemMapper inventoryItemMapper;
    private Clock clock;
    private InventoryItemServiceImpl service;

    @BeforeEach
    void setUp() {
        inventoryItemMapper = new InventoryItemMapper();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new InventoryItemServiceImpl(inventoryItemRepository, inventoryItemMapper, clock);
    }

    @Test
    void createConsumableSuccess() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "CON-001",
                "Guantes de nitrilo",
                "Guantes descartables",
                InventoryItemType.CONSUMABLE,
                "Protección personal",
                "Caja",
                20,
                5,
                LocalDate.of(2027, 8, 31),
                null, null, null
        );

        when(inventoryItemRepository.existsByCode("CON-001")).thenReturn(false);
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("CON-001");
        assertThat(response.name()).isEqualTo("Guantes de nitrilo");
        assertThat(response.type()).isEqualTo(InventoryItemType.CONSUMABLE);
        assertThat(response.status()).isEqualTo(InventoryItemStatus.ACTIVE);
        assertThat(response.unit()).isEqualTo("Caja");
        assertThat(response.currentStock()).isEqualTo(20);
        assertThat(response.minimumStock()).isEqualTo(5);
        assertThat(response.expirationDate()).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InventoryItemStatus.ACTIVE);
    }

    @Test
    void createInstrumentSuccess() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "INS-001",
                "Espejo dental",
                "Espejo intraoral n° 5",
                InventoryItemType.INSTRUMENT,
                "Diagnóstico",
                null, null, null, null,
                "Gabinete A",
                24,
                18
        );

        when(inventoryItemRepository.existsByCode("INS-001")).thenReturn(false);
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("INS-001");
        assertThat(response.name()).isEqualTo("Espejo dental");
        assertThat(response.type()).isEqualTo(InventoryItemType.INSTRUMENT);
        assertThat(response.status()).isEqualTo(InventoryItemStatus.ACTIVE);
        assertThat(response.location()).isEqualTo("Gabinete A");
        assertThat(response.totalQuantity()).isEqualTo(24);
        assertThat(response.availableQuantity()).isEqualTo(18);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createThrowsWhenCodeAlreadyExists() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", "Caja", 10, 2, null, null, null, null
        );

        when(inventoryItemRepository.existsByCode("CON-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CON-001");

        verify(inventoryItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void createThrowsWhenConsumableMissingUnit() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", "   ", 10, 2, null, null, null, null
        );

        when(inventoryItemRepository.existsByCode("CON-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unit is required");
    }

    @Test
    void createThrowsWhenConsumableStockNegative() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", "Caja", -1, 2, null, null, null, null
        );

        when(inventoryItemRepository.existsByCode("CON-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current stock");
    }

    @Test
    void createThrowsWhenInstrumentAvailableExceedsTotal() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "INS-001", "Espejo", null, InventoryItemType.INSTRUMENT,
                "Diagnóstico", null, null, null, null,
                "Gabinete A", 10, 15
        );

        when(inventoryItemRepository.existsByCode("INS-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Available quantity cannot exceed total quantity");
    }

    @Test
    void createThrowsWhenInstrumentMissingLocation() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "INS-001", "Espejo", null, InventoryItemType.INSTRUMENT,
                "Diagnóstico", null, null, null, null,
                "", 10, 5
        );

        when(inventoryItemRepository.existsByCode("INS-001")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Location is required");
    }

    @Test
    void findByIdSuccess() {
        UUID id = UUID.randomUUID();
        InventoryItem item = new InventoryItem(
                id, "CON-001", "Guantes", "Desc", InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.ACTIVE, "Caja", 10, 2, null,
                null, null, null, NOW, NOW
        );

        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));

        InventoryItemResponse response = service.findById(id);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Guantes");
    }

    @Test
    void findByIdNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Inventory item not found");
    }

    @Test
    void updateSuccess() {
        UUID id = UUID.randomUUID();
        InventoryItem existing = new InventoryItem(
                id, "CON-001", "Guantes viejos", "Desc", InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.ACTIVE, "Caja", 10, 2, null,
                null, null, null, NOW, NOW
        );

        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest(
                "CON-001", "Guantes nuevos", "Nueva desc", "Protección personal",
                InventoryItemStatus.ACTIVE, "Paquete", 5, LocalDate.of(2028, 1, 1), null
        );

        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = service.update(id, request);

        assertThat(response.name()).isEqualTo("Guantes nuevos");
        assertThat(response.category()).isEqualTo("Protección personal");
        assertThat(response.unit()).isEqualTo("Paquete");
        assertThat(response.minimumStock()).isEqualTo(5);
        assertThat(response.currentStock()).isEqualTo(10); // preserves existing stock
    }

    @Test
    void updateThrowsWhenNewCodeConflicts() {
        UUID id = UUID.randomUUID();
        InventoryItem existing = new InventoryItem(
                id, "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.ACTIVE, "Caja", 10, 2, null,
                null, null, null, NOW, NOW
        );

        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest(
                "CON-002", "Guantes", null, "Protección",
                InventoryItemStatus.ACTIVE, null, null, null, null
        );

        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.existsByCodeAndIdNot("CON-002", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CON-002");
    }

    @Test
    void updateStatusSuccess() {
        UUID id = UUID.randomUUID();
        InventoryItem existing = new InventoryItem(
                id, "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.ACTIVE, "Caja", 10, 2, null,
                null, null, null, NOW, NOW
        );

        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = service.updateStatus(id, InventoryItemStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(InventoryItemStatus.INACTIVE);
    }

    @Test
    void deactivateSetsStatusInactive() {
        UUID id = UUID.randomUUID();
        InventoryItem existing = new InventoryItem(
                id, "INS-001", "Espejo", null, InventoryItemType.INSTRUMENT,
                "Diagnóstico", InventoryItemStatus.ACTIVE, null, null, null, null,
                "Gabinete A", 10, 10, NOW, NOW
        );

        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate(id);

        assertThat(existing.getStatus()).isEqualTo(InventoryItemStatus.INACTIVE);
    }

    @Test
    void findAllReturnsPagedResults() {
        InventoryItem item1 = new InventoryItem(
                UUID.randomUUID(), "CON-001", "Guantes", null, InventoryItemType.CONSUMABLE,
                "Protección", InventoryItemStatus.ACTIVE, "Caja", 10, 2, null,
                null, null, null, NOW, NOW
        );
        Page<InventoryItem> page = new PageImpl<>(List.of(item1));

        when(inventoryItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<InventoryItemResponse> result = service.findAll(
                "guant", InventoryItemType.CONSUMABLE, "Protección", InventoryItemStatus.ACTIVE, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Guantes");
    }

    @Test
    void findAllThrowsWhenPageOrSizeInvalid() {
        assertThatThrownBy(() -> service.findAll(null, null, null, null, -1, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Page must not be negative");

        assertThatThrownBy(() -> service.findAll(null, null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Size must be at least 1");
    }
}
