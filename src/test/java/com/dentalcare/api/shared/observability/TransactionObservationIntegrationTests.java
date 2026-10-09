package com.dentalcare.api.shared.observability;

import com.dentalcare.api.modules.audit.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL deferred-constraint failure, after both observed audit calls have returned. */
@Testcontainers(disabledWithoutDocker = true)
class TransactionObservationIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    SimpleMeterRegistry registry;
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    AuditService observed;

    @BeforeEach void prepare() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS monitoring_audit_probe");
        jdbc.execute("CREATE TABLE monitoring_audit_probe (value integer UNIQUE DEFERRABLE INITIALLY DEFERRED)");
        manager = new DataSourceTransactionManager(source);
        registry = new SimpleMeterRegistry();
        registry.counter("dentalcare.audit.failures", "kind", "persistence");
        for (String result : java.util.List.of("rolled_back", "unknown"))
            registry.counter("dentalcare.transaction.commit.failures", "completion", result);
        var service = mock(AuditService.class);
        doAnswer(call -> { jdbc.update("INSERT INTO monitoring_audit_probe VALUES (1)"); return null; })
            .when(service).success("SYNTHETIC", "SECURITY", null, null, null);
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        observed = (AuditService) new OperationMetricsPostProcessor(factory.getBeanProvider(MeterRegistry.class))
            .postProcessAfterInitialization(service, "syntheticAudit");
    }
    private void audit() { observed.success("SYNTHETIC", "SECURITY", null, null, null); }
    private double commitFailures() {
        return registry.find("dentalcare.transaction.commit.failures").counters().stream()
            .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deferredCommitFailureIsCountedOnceWithoutAttributingItToAudit(boolean rollbackOnFailure) {
        manager.setRollbackOnCommitFailure(rollbackOnFailure);
        assertThrows(org.springframework.transaction.TransactionSystemException.class,
            () -> new TransactionTemplate(manager).execute(status -> { audit(); audit(); return null; }));
        assertEquals(1, commitFailures());
        assertEquals(0, registry.get("dentalcare.audit.failures").counter().count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM monitoring_audit_probe", Integer.class));
    }
    @Test void successfulCommitIsNotAFailure() {
        new TransactionTemplate(manager).execute(status -> { audit(); return null; });
        assertEquals(0, commitFailures());
        assertEquals(0, registry.get("dentalcare.audit.failures").counter().count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM monitoring_audit_probe", Integer.class));
    }
    @Test void unrelatedBusinessRollbackIsNotAnAuditOrCommitFailure() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status -> {
            audit(); throw new IllegalStateException("Synthetic unrelated business rollback");
        }));
        assertEquals(0, commitFailures());
        assertEquals(0, registry.get("dentalcare.audit.failures").counter().count());
    }
    @Test void rollbackOnlyIsNotACommitFailure() {
        new TransactionTemplate(manager).execute(status -> { audit(); status.setRollbackOnly(); return null; });
        assertEquals(0, commitFailures());
        assertEquals(0, registry.get("dentalcare.audit.failures").counter().count());
    }
}
