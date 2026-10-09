package com.dentalcare.api.modules.appointments.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dentalcare.appointment-outbox")
public record AppointmentOutboxProperties(String encryptionKey, int maxAttempts, Duration baseRetryDelay,
        Duration processingLease, int batchSize) {
    public AppointmentOutboxProperties {
        encryptionKey = encryptionKey == null ? "" : encryptionKey.trim();
        maxAttempts = maxAttempts < 1 ? 8 : Math.min(maxAttempts, 20);
        baseRetryDelay = baseRetryDelay == null || baseRetryDelay.isNegative() || baseRetryDelay.isZero()
                ? Duration.ofSeconds(30) : baseRetryDelay;
        processingLease = processingLease == null || processingLease.isNegative() || processingLease.isZero()
                ? Duration.ofMinutes(2) : processingLease;
        batchSize = batchSize < 1 ? 10 : Math.min(batchSize, 100);
    }
}
