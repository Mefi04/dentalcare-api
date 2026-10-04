package com.dentalcare.api.modules.settings.repository;

import com.dentalcare.api.modules.settings.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
class SettingsRepositoryIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @Autowired ClinicSettingsRepository clinic; @Autowired ProcedureCatalogItemRepository catalog;
    @Autowired JdbcTemplate jdbc; @Autowired EntityManager entityManager;
    private UUID actor;
    @BeforeEach void user(){actor=UUID.randomUUID();jdbc.update("INSERT INTO users (id, username, email, password_hash, status, created_at, updated_at, full_name, cui) VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)",
            actor,"settings-"+actor,"settings-"+actor+"@example.com","hash","Settings Actor","1234567890123");}
    @Test void migrationCreatesExactlyOneEmptySingleton(){assertThat(clinic.count()).isEqualTo(1);var value=clinic.findById((short)1).orElseThrow();assertThat(value.getTradeName()).isNull();assertThat(value.getUpdatedBy()).isNull();}
    @Test void singletonCheckRejectsSecondLogicalId(){assertThatThrownBy(()->jdbc.update("INSERT INTO clinic_settings (id, created_at, updated_at) VALUES (2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
            .isInstanceOf(Exception.class);}
    @Test void databaseRejectsCaseInsensitiveCodeAndNameDuplicates(){
        catalog.saveAndFlush(item("PROC-1","Cleaning"));entityManager.clear();
        assertThatThrownBy(()->catalog.saveAndFlush(item(" proc-1 ","Other"))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void databaseRejectsCaseInsensitiveNameDuplicates(){
        catalog.saveAndFlush(item("PROC-1","Cleaning"));entityManager.clear();
        assertThatThrownBy(()->catalog.saveAndFlush(item("PROC-2"," cleaning "))).isInstanceOf(DataIntegrityViolationException.class);
    }
    private ProcedureCatalogItem item(String code,String name){var now=Instant.now();return new ProcedureCatalogItem(UUID.randomUUID(),code,name,"Category",30,new BigDecimal("100.00"),ProcedureCatalogItemStatus.ACTIVE,actor,actor,now,now);}
}
