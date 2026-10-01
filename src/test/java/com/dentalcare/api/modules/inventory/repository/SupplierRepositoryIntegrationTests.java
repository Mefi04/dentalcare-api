package com.dentalcare.api.modules.inventory.repository;

import com.dentalcare.api.modules.inventory.model.Supplier;
import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SupplierRepositoryIntegrationTests {

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
    private SupplierRepository supplierRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindSupplier() {
        UUID id = UUID.randomUUID();
        Supplier supplier = new Supplier(
                id, "Dental Solutions S.A.", "Carlos Gomez", "12345678",
                "carlos@dentalsolutions.com", "Zone 10, Guatemala",
                "Entrega en 24 horas", SupplierStatus.ACTIVE, NOW, NOW
        );

        supplierRepository.saveAndFlush(supplier);
        entityManager.clear();

        Supplier found = supplierRepository.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("Dental Solutions S.A.");
        assertThat(found.getContactName()).isEqualTo("Carlos Gomez");
        assertThat(found.getStatus()).isEqualTo(SupplierStatus.ACTIVE);
    }

    @Test
    void blankNameFailsCheckConstraint() {
        Supplier supplier = new Supplier(
                UUID.randomUUID(), "   ", "Contact", "1234",
                "test@mail.com", "Address", "Notes", SupplierStatus.ACTIVE, NOW, NOW
        );

        assertThatThrownBy(() -> supplierRepository.saveAndFlush(supplier))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByNameQueriesAreCaseInsensitive() {
        Supplier supplier = new Supplier(
                UUID.randomUUID(), "Alpha Dental", "Contact", "1234",
                "alpha@mail.com", "Address", "Notes", SupplierStatus.ACTIVE, NOW, NOW
        );
        supplierRepository.saveAndFlush(supplier);

        assertThat(supplierRepository.existsByNameIgnoreCase("ALPHA DENTAL")).isTrue();
        assertThat(supplierRepository.existsByNameIgnoreCase("alpha dental")).isTrue();
        assertThat(supplierRepository.existsByNameIgnoreCaseAndIdNot("Alpha Dental", supplier.getId())).isFalse();
        assertThat(supplierRepository.existsByNameIgnoreCaseAndIdNot("Alpha Dental", UUID.randomUUID())).isTrue();
    }
}
