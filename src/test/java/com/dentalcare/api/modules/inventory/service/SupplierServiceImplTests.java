package com.dentalcare.api.modules.inventory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.inventory.dto.request.CreateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierRequest;
import com.dentalcare.api.modules.inventory.dto.request.UpdateSupplierStatusRequest;
import com.dentalcare.api.modules.inventory.dto.response.SupplierResponse;
import com.dentalcare.api.modules.inventory.mapper.SupplierMapper;
import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import com.dentalcare.api.modules.inventory.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private SupplierRepository supplierRepository;

    private final SupplierMapper supplierMapper = new SupplierMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        supplierService = new SupplierServiceImpl(supplierRepository, supplierMapper, clock);
    }

    @Test
    void createSuccessfullySavesNewSupplier() {
        CreateSupplierRequest request = new CreateSupplierRequest(
                "Dental Supplies Co", "Juan Lopez", "12345678",
                "juan@dentalsupplies.com", "Zone 10", "Main provider"
        );
        when(supplierRepository.existsByNameIgnoreCase("Dental Supplies Co")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = supplierService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Dental Supplies Co");
        assertThat(response.contactName()).isEqualTo("Juan Lopez");
        assertThat(response.phone()).isEqualTo("12345678");
        assertThat(response.email()).isEqualTo("juan@dentalsupplies.com");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(response.active()).isTrue();
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void createThrowsConflictWhenNameAlreadyExists() {
        CreateSupplierRequest request = new CreateSupplierRequest(
                "Existing Supplier", "Juan", "12345", "juan@mail.com", "Address", "Notes"
        );
        when(supplierRepository.existsByNameIgnoreCase("Existing Supplier")).thenReturn(true);

        assertThatThrownBy(() -> supplierService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createThrowsBadRequestWhenNameIsBlank() {
        CreateSupplierRequest request = new CreateSupplierRequest(
                "   ", "Juan", "12345", "juan@mail.com", "Address", "Notes"
        );

        assertThatThrownBy(() -> supplierService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void updateSuccessfullyModifiesSupplier() {
        UUID id = UUID.randomUUID();
        Supplier existing = new Supplier(id, "Old Name", "Old Contact", "1111", "old@mail.com",
                "Old Address", "Old Notes", SupplierStatus.ACTIVE, NOW.minusSeconds(3600), NOW.minusSeconds(3600));

        UpdateSupplierRequest request = new UpdateSupplierRequest(
                "New Name", "New Contact", "2222", "new@mail.com", "New Address", "New Notes"
        );

        when(supplierRepository.findById(id)).thenReturn(Optional.of(existing));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot("New Name", id)).thenReturn(false);
        when(supplierRepository.save(existing)).thenReturn(existing);

        SupplierResponse response = supplierService.update(id, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.contactName()).isEqualTo("New Contact");
        assertThat(response.phone()).isEqualTo("2222");
        assertThat(response.email()).isEqualTo("new@mail.com");
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateThrowsConflictWhenNameBelongsToAnotherSupplier() {
        UUID id = UUID.randomUUID();
        Supplier existing = new Supplier(id, "Old Name", "Old Contact", "1111", "old@mail.com",
                "Old Address", "Old Notes", SupplierStatus.ACTIVE, NOW, NOW);

        UpdateSupplierRequest request = new UpdateSupplierRequest(
                "Duplicate Name", "Contact", "2222", "mail@mail.com", "Address", "Notes"
        );

        when(supplierRepository.findById(id)).thenReturn(Optional.of(existing));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot("Duplicate Name", id)).thenReturn(true);

        assertThatThrownBy(() -> supplierService.update(id, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateStatusInactivatesSupplierWithoutPhysicalDelete() {
        UUID id = UUID.randomUUID();
        Supplier existing = new Supplier(id, "Vendor", "Contact", "1111", "v@mail.com",
                "Address", "Notes", SupplierStatus.ACTIVE, NOW, NOW);

        when(supplierRepository.findById(id)).thenReturn(Optional.of(existing));
        when(supplierRepository.save(existing)).thenReturn(existing);

        SupplierResponse response = supplierService.updateStatus(id, new UpdateSupplierStatusRequest(SupplierStatus.INACTIVE));

        assertThat(response.status()).isEqualTo(SupplierStatus.INACTIVE);
        assertThat(response.active()).isFalse();
        assertThat(existing.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void findByIdReturnsSupplierWhenFound() {
        UUID id = UUID.randomUUID();
        Supplier existing = new Supplier(id, "Vendor", "Contact", "1111", "v@mail.com",
                "Address", "Notes", SupplierStatus.ACTIVE, NOW, NOW);

        when(supplierRepository.findById(id)).thenReturn(Optional.of(existing));

        SupplierResponse response = supplierService.findById(id);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    void findByIdThrowsResourceNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(supplierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Supplier not found");
    }

    @Test
    void findAllAppliesPaginationAndFiltering() {
        Supplier supplier = new Supplier(UUID.randomUUID(), "Vendor A", "Contact", "1111", "v@mail.com",
                "Address", "Notes", SupplierStatus.ACTIVE, NOW, NOW);
        when(supplierRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier)));

        Page<SupplierResponse> page = supplierService.findAll("Vendor", SupplierStatus.ACTIVE, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).name()).isEqualTo("Vendor A");
    }
}
