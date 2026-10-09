package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_public_decisions")
public class AppointmentPublicDecision {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appointment_request_id", nullable = false, updatable = false)
    private UUID appointmentRequestId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 10, updatable = false)
    private String decision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentPublicDecision() {}

    public AppointmentPublicDecision(UUID requestId, UUID key, String decision, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.appointmentRequestId = requestId;
        this.idempotencyKey = key;
        this.decision = decision;
        this.createdAt = createdAt;
    }

    public UUID getAppointmentRequestId() { return appointmentRequestId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getDecision() { return decision; }
}
