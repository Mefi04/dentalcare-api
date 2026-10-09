package com.dentalcare.api.shared.observability;

import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.clinicalrecords.storage.*;
import com.dentalcare.api.security.ratelimit.DatabaseRateLimitRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Measures existing boundaries without new queries or changes to business transactions. */
@Component
public class OperationMetricsPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<MeterRegistry> registries;
    public OperationMetricsPostProcessor(ObjectProvider<MeterRegistry> registries) { this.registries = registries; }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        boolean audit = bean instanceof AuditService;
        boolean storage = bean instanceof ClinicalDocumentStorage;
        boolean database = bean instanceof org.springframework.data.repository.Repository
            || bean instanceof DatabaseRateLimitRepository
            || org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                org.springframework.aop.support.AopUtils.getTargetClass(bean), org.springframework.stereotype.Repository.class) != null;
        if (!audit && !storage && !database) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        if (bean instanceof DatabaseRateLimitRepository) proxy.setProxyTargetClass(true);
        proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            String operation = invocation.getMethod().getName();
            if (invocation.getMethod().getDeclaringClass() == Object.class) return invocation.proceed();
            if (audit || database) observeExistingTransaction();
            long started = System.nanoTime();
            String result = "success";
            try {
                Object value = invocation.proceed();
                if (storage && value instanceof StoredDocumentContent content) {
                    return new StoredDocumentContent(content.storageObjectKey(), content.contentType(),
                        content.contentLength(), transfer(content.content(), content.contentLength()));
                }
                return value;
            } catch (Throwable error) {
                result = "failure";
                org.slf4j.LoggerFactory.getLogger(OperationMetricsPostProcessor.class).atWarn()
                    .addKeyValue("event", audit ? "audit_failure" : storage ? "storage_failure" : "database_failure")
                    .log("Operation failed");
                if (audit) count("dentalcare.audit.failures", "kind", "persistence");
                if (database && error instanceof DataAccessException) {
                    count("dentalcare.database.errors", "kind", databaseKind(error));
                }
                throw error;
            } finally {
                if (storage && Set.of("store", "load", "getMetadata", "exists", "delete").contains(operation)) {
                    MeterRegistry registry = registries.getIfAvailable();
                    if (registry != null) registry.timer("dentalcare.storage.operations", "operation", operation, "result", result)
                        .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
            }
        });
        return proxy.getProxy();
    }
    private void observeExistingTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(CommitObservation.class::isInstance)) return;
        TransactionSynchronizationManager.registerSynchronization(new CommitObservation());
    }
    private final class CommitObservation implements TransactionSynchronization {
        private boolean confirmationStarted;
        @Override public void beforeCommit(boolean readOnly) { confirmationStarted = true; }
        @Override public void afterCompletion(int status) {
            // No cause is supplied by Spring. Never attribute this outcome to an audit write.
            // Explicit/rollback-only rollbacks before confirmation are not commit failures.
            if (confirmationStarted && status != STATUS_COMMITTED) {
                count("dentalcare.transaction.commit.failures", "completion",
                    status == STATUS_ROLLED_BACK ? "rolled_back" : "unknown");
            }
        }
    }
    private String databaseKind(Throwable error) {
        if (error instanceof org.springframework.dao.QueryTimeoutException) return "timeout";
        if (error instanceof org.springframework.dao.DataIntegrityViolationException) return "constraint";
        if (error instanceof org.springframework.dao.DataAccessResourceFailureException) return "connection";
        return "other";
    }
    private void count(String name, String tag, String value) {
        MeterRegistry registry = registries.getIfAvailable();
        if (registry != null) registry.counter(name, tag, value).increment();
    }
    InputStream transfer(InputStream input, long expected) {
        return new FilterInputStream(input) {
            private final AtomicBoolean completed = new AtomicBoolean();
            private long bytes;
            private void finish(String result) {
                if (completed.compareAndSet(false, true)) count("dentalcare.storage.transfers", "result", result);
            }
            @Override public int read() throws IOException {
                try { int value = in.read(); if (value < 0) finish(bytes < expected ? "failure" : "success"); else bytes++; return value; }
                catch (IOException error) { finish("failure"); throw error; }
            }
            @Override public int read(byte[] data, int offset, int length) throws IOException {
                try { int read = in.read(data, offset, length); if (read < 0) finish(bytes < expected ? "failure" : "success"); else bytes += read; return read; }
                catch (IOException error) { finish("failure"); throw error; }
            }
            @Override public void close() throws IOException {
                try { super.close(); finish(expected > 0 && bytes >= expected ? "success" : "cancelled"); }
                catch (IOException error) { finish("failure"); throw error; }
            }
        };
    }
}
