package com.dentalcare.api.modules.appointments.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_requests")
public class AppointmentRequest {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_professional_id", nullable = false)
    private User requestedProfessional;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_professional_id")
    private User proposedProfessional;

    @Column(name = "proposed_at")
    private Instant proposedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppointmentRequest() {}

    public AppointmentRequest(UUID id, Patient patient, User requestedProfessional, Instant requestedAt,
                              AppointmentRequestStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.requestedProfessional = requestedProfessional;
        this.requestedAt = requestedAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getRequestedProfessional() { return requestedProfessional; }
    public Instant getRequestedAt() { return requestedAt; }
    public User getProposedProfessional() { return proposedProfessional; }
    public Instant getProposedAt() { return proposedAt; }
    public AppointmentRequestStatus getStatus() { return status; }
    public User getProcessedBy() { return processedBy; }
    public Appointment getAppointment() { return appointment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void propose(User professional, Instant scheduledAt, User actor, Instant now) {
        proposedProfessional = professional;
        proposedAt = scheduledAt;
        processedBy = actor;
        status = AppointmentRequestStatus.PROPOSED;
        updatedAt = now;
    }

    public void confirm(Appointment confirmedAppointment, User actor, Instant now) {
        appointment = confirmedAppointment;
        processedBy = actor;
        status = AppointmentRequestStatus.CONFIRMED;
        updatedAt = now;
    }

    public void reject(User actor, Instant now) {
        processedBy = actor;
        status = AppointmentRequestStatus.REJECTED;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = AppointmentRequestStatus.CANCELLED;
        updatedAt = now;
    }
}
