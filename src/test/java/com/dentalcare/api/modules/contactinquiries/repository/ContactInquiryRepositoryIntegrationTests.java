package com.dentalcare.api.modules.contactinquiries.repository;

import com.dentalcare.api.modules.contactinquiries.model.ContactInquiry;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryReason;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ContactInquiryRepositoryIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private ContactInquiryRepository repository;

    @ParameterizedTest
    @EnumSource(ContactInquiryReason.class)
    @DisplayName("persists and reads back contact inquiries with all 7 contract reasons in PostgreSQL")
    void persistsInquiryWithAllContractReasons(ContactInquiryReason reason) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        ContactInquiry inquiry = new ContactInquiry(
                id,
                "Paciente Prueba",
                "prueba@example.com",
                "5555-1234",
                reason,
                "Mensaje sobre " + reason.name(),
                true,
                now
        );

        ContactInquiry saved = repository.saveAndFlush(inquiry);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getReason()).isEqualTo(reason);
        assertThat(saved.getStatus()).isEqualTo(ContactInquiryStatus.NEW);

        var retrieved = repository.findById(id);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("queries inquiries by status with pagination")
    void queriesByStatusWithPagination() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        ContactInquiry inquiry = new ContactInquiry(
                id,
                "Juan Perez",
                "juan@example.com",
                null,
                ContactInquiryReason.APPOINTMENT_HELP,
                "Necesito ayuda para agendar",
                true,
                now
        );
        repository.saveAndFlush(inquiry);

        var page = repository.findByStatus(ContactInquiryStatus.NEW, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(ContactInquiry::getId).contains(id);
    }
}

