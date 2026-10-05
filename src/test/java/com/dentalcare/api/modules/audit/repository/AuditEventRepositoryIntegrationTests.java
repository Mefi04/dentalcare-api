package com.dentalcare.api.modules.audit.repository;

import com.dentalcare.api.modules.audit.model.AuditEvent;
import com.dentalcare.api.modules.audit.model.AuditResult;
import com.dentalcare.api.modules.audit.dto.AuditEventResponse;
import com.dentalcare.api.modules.audit.service.AuditQueryService;
import com.dentalcare.api.modules.audit.service.AuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
@Import({AuditQueryService.class, AuditServiceImpl.class, AuditEventRepositoryIntegrationTests.ClockConfiguration.class})
class AuditEventRepositoryIntegrationTests {
    @TestConfiguration static class ClockConfiguration { @Bean Clock testClock(){return Clock.systemUTC();} }
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired AuditEventRepository repository;
    @Autowired AuditQueryService query;
    @Autowired AuditServiceImpl audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void liquibaseCreatesAuditSchemaIndexesAndAdministratorOnlyPermission(){
        assertThat(jdbc.queryForObject("select to_regclass('public.audit_events') is not null",Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename='audit_events'",String.class))
                .contains("idx_audit_events_occurred_at","idx_audit_events_actor","idx_audit_events_module","idx_audit_events_action");
        assertThat(jdbc.queryForList("select r.code from role_permissions rp join roles r on r.id=rp.role_id join permissions p on p.id=rp.permission_id where p.code='AUDIT_READ'",String.class))
                .containsExactly("ADMINISTRATOR");
    }

    @Test void persistsAndFiltersEventsInPostgresWithDescendingOrder(){
        Instant now=Instant.parse("2026-02-01T00:00:00Z");
        UUID actor=UUID.randomUUID();
        jdbc.update("insert into users(id,username,email,password_hash,status,created_at,updated_at,cui,full_name) values (?,?,?,?,?,?,?,?,?)",
                actor,"audit-test","audit-test@example.invalid","not-a-password","ACTIVE",java.sql.Timestamp.from(now),java.sql.Timestamp.from(now),"1234567890123","Test Actor");
        repository.save(new AuditEvent(UUID.randomUUID(),now.minusSeconds(10),actor,null,"BILLING_PAYMENT_CREATED","BILLING","Payment",UUID.randomUUID().toString(),AuditResult.SUCCESS,"Action completed"));
        repository.save(new AuditEvent(UUID.randomUUID(),now,null,null,"AUTH_LOGIN_FAILED","AUTH","User",null,AuditResult.FAILURE,"Action failed"));
        repository.flush();
        Page<AuditEventResponse> all=query.search(now.minusSeconds(100),now,null,null,null,null,null,0,10);
        assertThat(all.getContent()).hasSize(2);
        assertThat(all.getSort().getOrderFor("occurredAt").getDirection().name()).isEqualTo("DESC");
        assertThat(all.getContent()).extracting(AuditEventResponse::actionCode)
                .containsExactly("AUTH_LOGIN_FAILED","BILLING_PAYMENT_CREATED");
        assertThat(query.search(now.minusSeconds(5),now,null,null,null,null,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,now.minusSeconds(5),null,null,null,null,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,null,actor,null,null,null,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,null,null,"BILLING",null,null,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,null,null,null,"BILLING_PAYMENT_CREATED",null,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,null,null,null,null,AuditResult.SUCCESS,null,0,10).getTotalElements()).isEqualTo(1);
        assertThat(query.search(null,null,null,null,null,null,"payment",0,10).getTotalElements()).isEqualTo(1);
        Page<?> filtered=query.search(now.minusSeconds(100),now,actor,"BILLING","BILLING_PAYMENT_CREATED",AuditResult.SUCCESS,"payment",0,10);
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(query.search(now.minusSeconds(100),now,null,null,null,null,null,0,1).getTotalPages()).isEqualTo(2);
    }

    @Test void serviceRecordsPersistableSuccessAndFailure(){
        audit.success("TEST_SUCCESS_AUDIT","USERS","User",UUID.randomUUID(),null);
        audit.failure("TEST_FAILURE_AUDIT","AUTH","User",null,null);
        repository.flush();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action_code in ('TEST_SUCCESS_AUDIT','TEST_FAILURE_AUDIT')",Long.class)).isEqualTo(2);
        assertThat(repository.findAll(PageRequest.of(0,10)).getContent()).extracting(AuditEvent::getResult)
                .contains(AuditResult.SUCCESS,AuditResult.FAILURE);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void successRollsBackWithBusinessTransactionWhileFailureCommitsIndependently(){
        long baseline=repository.count();
        TransactionTemplate tx=new TransactionTemplate(transactionManager);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->tx.execute(status->{
            audit.success("STAFF_CREATED","USERS","User",UUID.randomUUID(),null);
            throw new IllegalStateException("business operation failed after audit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(baseline);

        org.assertj.core.api.Assertions.assertThatThrownBy(()->tx.execute(status->{
            audit.failure("AUTH_LOGIN_FAILED","AUTH","User",null,null);
            throw new IllegalStateException("authentication failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(baseline+1);
    }
}
