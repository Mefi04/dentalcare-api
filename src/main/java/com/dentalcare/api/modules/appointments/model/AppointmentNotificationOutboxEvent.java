package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_notification_outbox")
public class AppointmentNotificationOutboxEvent {
    @Id
    private UUID id;
    @Column(name = "appointment_request_id", nullable = false, updatable = false)
    private UUID appointmentRequestId;
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private String eventType;
    @Column(nullable = false, length = 10, updatable = false)
    private String channel;
    @Column(name = "recipient_ciphertext", columnDefinition = "TEXT")
    private String recipientCiphertext;
    @Column(name = "payload_ciphertext", columnDefinition = "TEXT")
    private String payloadCiphertext;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentNotificationStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "processing_started_at")
    private Instant processingStartedAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    protected AppointmentNotificationOutboxEvent() {}

    public AppointmentNotificationOutboxEvent(UUID id, UUID requestId, String eventType, String channel,
            String recipientCiphertext, String payloadCiphertext, UUID correlationId, String idempotencyKey,
            Instant now) {
        this.id = id;
        this.appointmentRequestId = requestId;
        this.eventType = eventType;
        this.channel = channel;
        this.recipientCiphertext = recipientCiphertext;
        this.payloadCiphertext = payloadCiphertext;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
        this.status = AppointmentNotificationStatus.PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public void claim(Instant now) {
        status = AppointmentNotificationStatus.PROCESSING;
        attempts++;
        lastAttemptAt = now;
        processingStartedAt = now;
    }

    public void markSent(Instant now) {
        status = AppointmentNotificationStatus.SENT;
        sentAt = now;
        processingStartedAt = null;
        lastErrorCode = null;
        recipientCiphertext = null;
        payloadCiphertext = null;
    }

    public void markFailure(String safeErrorCode, Instant retryAt, boolean deadLetter) {
        status = deadLetter ? AppointmentNotificationStatus.DEAD_LETTER : AppointmentNotificationStatus.RETRY_PENDING;
        lastErrorCode = safeErrorCode;
        nextAttemptAt = retryAt;
        processingStartedAt = null;
        if (deadLetter) {
            recipientCiphertext = null;
            payloadCiphertext = null;
        }
    }

    public void markExpired(String safeErrorCode) {
        status = AppointmentNotificationStatus.DEAD_LETTER;
        lastErrorCode = safeErrorCode;
        processingStartedAt = null;
        recipientCiphertext = null;
        payloadCiphertext = null;
    }

    public UUID getId() { return id; }
    public UUID getAppointmentRequestId() { return appointmentRequestId; }
    public String getEventType() { return eventType; }
    public String getChannel() { return channel; }
    public String getRecipientCiphertext() { return recipientCiphertext; }
    public String getPayloadCiphertext() { return payloadCiphertext; }
    public AppointmentNotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public UUID getCorrelationId() { return correlationId; }
}
