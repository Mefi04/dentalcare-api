package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_public_conversation_tokens")
public class AppointmentPublicConversationToken {
    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "appointment_request_id", nullable = false)
    private UUID appointmentRequestId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AppointmentPublicConversationToken() {}

    public AppointmentPublicConversationToken(String tokenHash, UUID appointmentRequestId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.appointmentRequestId = appointmentRequestId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getAppointmentRequestId() { return appointmentRequestId; }
    public Instant getExpiresAt() { return expiresAt; }

    public void extendExpiry(Instant newExpiresAt) {
        if (newExpiresAt != null && (this.expiresAt == null || newExpiresAt.isAfter(this.expiresAt))) {
            this.expiresAt = newExpiresAt;
        }
    }
}
