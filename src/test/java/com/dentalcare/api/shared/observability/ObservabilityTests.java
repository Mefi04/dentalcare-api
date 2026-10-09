package com.dentalcare.api.shared.observability;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilityTests {
    @Test void unknownLengthCloseDoesNotClaimSuccessfulTransferWithoutEof() throws Exception {
        var registry = new SimpleMeterRegistry();
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        var processor = new OperationMetricsPostProcessor(factory.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        processor.transfer(new ByteArrayInputStream(new byte[2]), 0).close();
        assertEquals(1, registry.get("dentalcare.storage.transfers").tag("result", "cancelled").counter().count());
    }
    @Test void storageFailureIsMeasuredWithoutChangingExceptionOrCallingProviderAgain() {
        var registry = new SimpleMeterRegistry();
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        var processor = new OperationMetricsPostProcessor(factory.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        var storage = org.mockito.Mockito.mock(com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage.class);
        var failure = new com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException("PRIVATE_R2");
        org.mockito.Mockito.when(storage.load("PRIVATE_OBJECT_KEY")).thenThrow(failure);
        var observed = (com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage) processor.postProcessAfterInitialization(storage, "storage");
        assertSame(failure, assertThrows(com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException.class,
            () -> observed.load("PRIVATE_OBJECT_KEY")));
        assertEquals(1, registry.get("dentalcare.storage.operations").tag("operation", "load").tag("result", "failure").timer().count());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.times(1)).load("PRIVATE_OBJECT_KEY");
        assertFalse(registry.getMeters().toString().contains("PRIVATE_"));
    }
    interface FailingRepository extends org.springframework.data.repository.Repository<Object, Long> { void query(); }
    @Test void repositoryAndAuditFailuresAreCountedWithoutNewQueries() {
        var registry = new SimpleMeterRegistry();
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        var processor = new OperationMetricsPostProcessor(factory.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        var repository = org.mockito.Mockito.mock(FailingRepository.class);
        org.mockito.Mockito.doThrow(new org.springframework.dao.QueryTimeoutException("PRIVATE_SQL"))
            .when(repository).query();
        var decorated = (FailingRepository) processor.postProcessAfterInitialization(repository, "repository");
        assertThrows(org.springframework.dao.QueryTimeoutException.class, decorated::query);
        assertEquals(1, registry.get("dentalcare.database.errors").tag("kind", "timeout").counter().count());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1)).query();
        var audit = org.mockito.Mockito.mock(com.dentalcare.api.modules.audit.service.AuditService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("PRIVATE_SQL"))
            .when(audit).failure("SAFE_ACTION", "SECURITY", null, null, null);
        var observedAudit = (com.dentalcare.api.modules.audit.service.AuditService) processor.postProcessAfterInitialization(audit, "audit");
        assertThrows(IllegalStateException.class, () -> observedAudit.failure("SAFE_ACTION", "SECURITY", null, null, null));
        assertEquals(1, registry.get("dentalcare.audit.failures").counter().count());
    }
    @Test void serverGeneratesIdAndCleansContextEvenOnFailure() throws Exception {
        var filter = new CorrelationIdFilter();
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "patient-secret");
        var response = new MockHttpServletResponse();
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            assertEquals(response.getHeader("X-Request-ID"), MDC.get("request_id"));
            throw new jakarta.servlet.ServletException("secret");
        }));
        assertDoesNotThrow(() -> java.util.UUID.fromString(response.getHeader("X-Request-ID")));
        assertNull(MDC.get("request_id"));
    }
    @Test void encoderExcludesMessagesArgumentsCausesAndArbitraryMdc() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger("test.safe");
        MDC.put("patient", "PRIVATE_PATIENT");
        try {
            var event = new LoggingEvent("test", logger, Level.ERROR, "JWT {} OTP PRIVATE_OTP", new RuntimeException("PRIVATE_SQL", new IOException("PRIVATE_R2")), new Object[]{"PRIVATE_JWT"});
            var json = new String(new SafeJsonEncoder().encode(event), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("PRIVATE_"));
            assertTrue(json.contains("java.lang.RuntimeException"));
            assertFalse(json.contains("stack_trace"));
        } finally { MDC.clear(); }
    }
    @Test void streamFailureIsCountedOnceAndExceptionPreserved() throws Exception {
        var registry = new SimpleMeterRegistry();
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        var processor = new OperationMetricsPostProcessor(factory.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        var stream = processor.transfer(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("PRIVATE_CONTENT"); }
        }, 5);
        assertThrows(IOException.class, stream::read);
        stream.close();
        assertEquals(1, registry.get("dentalcare.storage.transfers").tag("result", "failure").counter().count());
    }
    @Test void earlyCloseAndTruncatedTransferAreDistinguished() throws Exception {
        var registry = new SimpleMeterRegistry();
        var factory = new StaticListableBeanFactory(java.util.Map.of("registry", registry));
        var processor = new OperationMetricsPostProcessor(factory.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        processor.transfer(new ByteArrayInputStream(new byte[2]), 5).close();
        try (var stream = processor.transfer(new ByteArrayInputStream(new byte[2]), 5)) { stream.readAllBytes(); }
        assertEquals(1, registry.get("dentalcare.storage.transfers").tag("result", "cancelled").counter().count());
        assertEquals(1, registry.get("dentalcare.storage.transfers").tag("result", "failure").counter().count());
    }
}
