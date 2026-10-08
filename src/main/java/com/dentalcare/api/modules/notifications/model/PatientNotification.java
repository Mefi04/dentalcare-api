package com.dentalcare.api.modules.notifications.model;

import com.dentalcare.api.modules.patients.model.Patient;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_notifications")
public class PatientNotification {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationEventType eventType;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PatientNotification() {}

    public PatientNotification(UUID id, Patient patient, NotificationEventType eventType,
                               String title, String message, Instant readAt, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.eventType = eventType;
        this.title = title;
        this.message = message;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public NotificationEventType getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
