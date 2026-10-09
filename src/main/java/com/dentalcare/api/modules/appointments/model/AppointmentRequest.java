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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_professional_id")
    private User requestedProfessional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_professional_id")
    private User assignedProfessional;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_professional_id")
    private User proposedProfessional;

    @Column(name = "proposed_at")
    private Instant proposedAt;

    @Column(name = "proposed_expires_at")
    private Instant proposedExpiresAt;

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

    @Column(name = "requester_full_name", length = 150)
    private String requesterFullName;

    @Column(name = "requester_cui", length = 13)
    private String requesterCui;

    @Column(name = "requester_phone", length = 30)
    private String requesterPhone;

    @Column(name = "requester_email", length = 255)
    private String requesterEmail;

    @Column(name = "request_reason", length = 300)
    private String requestReason;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "idempotency_payload_hash", length = 64)
    private String idempotencyPayloadHash;

    @Column(name = "requester_identity_verified_at")
    private Instant requesterIdentityVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_identity_verified_by")
    private User requesterIdentityVerifiedBy;

    @Column(name = "requester_identity_verification_method", length = 32)
    private String requesterIdentityVerificationMethod;

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

    public AppointmentRequest(UUID id, Patient patient, User requestedProfessional, Instant requestedAt,
                              AppointmentRequestStatus status, Instant createdAt, Instant updatedAt,
                              String requesterFullName, String requesterCui, String requesterPhone,
                              String requesterEmail, String requestReason, UUID idempotencyKey,
                              String idempotencyPayloadHash) {
        this(id, patient, requestedProfessional, requestedAt, status, createdAt, updatedAt);
        this.requesterFullName = requesterFullName;
        this.requesterCui = requesterCui;
        this.requesterPhone = requesterPhone;
        this.requesterEmail = requesterEmail;
        this.requestReason = requestReason;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyPayloadHash = idempotencyPayloadHash;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getRequestedProfessional() { return requestedProfessional; }
    public User getAssignedProfessional() { return assignedProfessional; }
    public Instant getRequestedAt() { return requestedAt; }
    public User getProposedProfessional() { return proposedProfessional; }
    public Instant getProposedAt() { return proposedAt; }
    public Instant getProposedExpiresAt() { return proposedExpiresAt; }
    public AppointmentRequestStatus getStatus() { return status; }
    public User getProcessedBy() { return processedBy; }
    public Appointment getAppointment() { return appointment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getRequesterFullName() { return requesterFullName; }
    public String getRequesterCui() { return requesterCui; }
    public String getRequesterPhone() { return requesterPhone; }
    public String getRequesterEmail() { return requesterEmail; }
    public String getRequestReason() { return requestReason; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }
    public Instant getRequesterIdentityVerifiedAt() { return requesterIdentityVerifiedAt; }
    public User getRequesterIdentityVerifiedBy() { return requesterIdentityVerifiedBy; }
    public String getRequesterIdentityVerificationMethod() { return requesterIdentityVerificationMethod; }

    public void linkPatient(Patient patient, Instant now) {
        this.patient = patient;
        this.updatedAt = now;
    }

    public void verifyRequesterIdentity(User actor, String method, Instant now) {
        this.requesterIdentityVerifiedBy = actor;
        this.requesterIdentityVerificationMethod = method;
        this.requesterIdentityVerifiedAt = now;
        this.updatedAt = now;
    }

    public void assignProfessional(User professional, Instant now) {
        this.assignedProfessional = professional;
        this.updatedAt = now;
    }

    public void propose(User professional, Instant scheduledAt, User actor, Instant now) {
        propose(professional, scheduledAt, null, actor, now);
    }

    public void propose(User professional, Instant scheduledAt, Instant expiresAt, User actor, Instant now) {
        proposedProfessional = professional;
        proposedAt = scheduledAt;
        proposedExpiresAt = expiresAt;
        processedBy = actor;
        status = requesterFullName == null ? AppointmentRequestStatus.PROPOSED : AppointmentRequestStatus.PENDING_PATIENT;
        updatedAt = now;
    }

    public void returnToClinic(Instant now) {
        status = requesterFullName == null ? AppointmentRequestStatus.PENDING : AppointmentRequestStatus.PENDING_CLINIC;
        proposedProfessional = null;
        proposedAt = null;
        proposedExpiresAt = null;
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
