package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_request_messages")
public class AppointmentRequestMessage {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appointment_request_id", nullable = false, updatable = false)
    private UUID appointmentRequestId;

    @Column(name = "sender", nullable = false, length = 10)
    private String sender;

    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType;

    @Column(name = "message_text", nullable = false, length = 500)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentRequestMessage() {}
    public AppointmentRequestMessage(UUID id, UUID requestId, String sender, String type, String text, Instant createdAt) {
        this.id = id; this.appointmentRequestId = requestId; this.sender = sender;
        this.messageType = type; this.text = text; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getAppointmentRequestId() { return appointmentRequestId; }
    public String getSender() { return sender; }
    public String getMessageType() { return messageType; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
