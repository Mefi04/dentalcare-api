package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_public_conversations")
public class AppointmentPublicConversation {
    @Id
    @Column(name = "appointment_request_id", nullable = false, updatable = false)
    private UUID appointmentRequestId;

    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @Column(name = "verification_code_hash", length = 100)
    private String verificationCodeHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;

    @Column(name = "conversation_token_hash", length = 64)
    private String conversationTokenHash;

    @Column(name = "conversation_expires_at")
    private Instant conversationExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppointmentPublicConversation() {}

    public AppointmentPublicConversation(UUID requestId, String channel, String codeHash, Instant codeExpiresAt, Instant now) {
        this.appointmentRequestId = requestId;
        this.channel = channel;
        this.verificationCodeHash = codeHash;
        this.verificationExpiresAt = codeExpiresAt;
        this.updatedAt = now;
    }

    public UUID getAppointmentRequestId() { return appointmentRequestId; }
    public String getChannel() { return channel; }
    public String getVerificationCodeHash() { return verificationCodeHash; }
    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public int getVerificationAttempts() { return verificationAttempts; }
    public String getConversationTokenHash() { return conversationTokenHash; }
    public Instant getConversationExpiresAt() { return conversationExpiresAt; }

    public void replaceCode(String channel, String hash, Instant expiresAt, Instant now) {
        this.channel = channel;
        this.verificationCodeHash = hash;
        this.verificationExpiresAt = expiresAt;
        this.verificationAttempts = 0;
        this.conversationTokenHash = null;
        this.conversationExpiresAt = null;
        this.updatedAt = now;
    }

    public void registerFailedAttempt(Instant now) { verificationAttempts++; updatedAt = now; }
    public void consumeCode(String tokenHash, Instant expiresAt, Instant now) {
        verificationCodeHash = null;
        verificationExpiresAt = null;
        conversationTokenHash = tokenHash;
        conversationExpiresAt = expiresAt;
        updatedAt = now;
    }
}
