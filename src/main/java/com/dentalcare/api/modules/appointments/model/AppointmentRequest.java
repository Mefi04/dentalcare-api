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

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AppointmentRequestSource source = AppointmentRequestSource.PATIENT_PORTAL;

    @Column(name = "requester_full_name", length = 150)
    private String requesterFullName;

    @Column(name = "requester_cui", length = 13)
    private String requesterCui;

    @Column(name = "requester_birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "requester_gender", length = 20)
    private String gender;

    @Column(name = "requester_alternative_id", length = 100)
    private String alternativeId;

    @Column(name = "requester_guardian_name", length = 150)
    private String guardianName;

    @Column(name = "requester_guardian_relationship", length = 100)
    private String guardianRelationship;

    @Column(name = "requester_guardian_phone", length = 30)
    private String guardianPhone;

    @Column(name = "requester_phone", length = 30)
    private String requesterPhone;

    @Column(name = "requester_email", length = 255)
    private String requesterEmail;

    @Column(name = "requester_department", length = 100)
    private String department;

    @Column(name = "requester_municipality", length = 100)
    private String municipality;

    @Column(name = "requester_address", length = 255)
    private String address;

    @Column(name = "requester_emergency_name", length = 150)
    private String emergencyName;

    @Column(name = "requester_emergency_phone", length = 30)
    private String emergencyPhone;

    @Column(name = "requester_nit", length = 30)
    private String nit;

    @Column(name = "requester_billing_name", length = 150)
    private String billingName;

    @Column(name = "requester_billing_address", length = 255)
    private String billingAddress;

    @Column(name = "request_reason", length = 300)
    private String requestReason;

    @Column(name = "privacy_accepted", nullable = false)
    private boolean privacyAccepted;

    @Column(name = "privacy_notice_version", length = 40)
    private String privacyNoticeVersion;

    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "idempotency_payload_hash", length = 64)
    private String idempotencyPayloadHash;

    protected AppointmentRequest() {}

    public AppointmentRequest(UUID id, Patient patient, User requestedProfessional, Instant requestedAt,
                              AppointmentRequestStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.requestedProfessional = requestedProfessional;
        this.requestedAt = requestedAt;
        this.status = status;
        this.source = AppointmentRequestSource.PATIENT_PORTAL;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public AppointmentRequest(UUID id, Patient patient, User requestedProfessional, Instant requestedAt,
                              AppointmentRequestStatus status, Instant createdAt, Instant updatedAt,
                              AppointmentRequestSource source,
                              String requesterFullName, String requesterCui, java.time.LocalDate birthDate,
                              String gender, String alternativeId, String guardianName, String guardianRelationship,
                              String guardianPhone, String requesterPhone, String requesterEmail,
                              String department, String municipality, String address,
                              String emergencyName, String emergencyPhone, String nit,
                              String billingName, String billingAddress, String requestReason,
                              boolean privacyAccepted, String privacyNoticeVersion, Instant privacyAcceptedAt,
                              UUID idempotencyKey, String idempotencyPayloadHash) {
        this(id, patient, requestedProfessional, requestedAt, status, createdAt, updatedAt);
        this.source = source != null ? source : AppointmentRequestSource.PUBLIC;
        this.requesterFullName = requesterFullName;
        this.requesterCui = requesterCui;
        this.birthDate = birthDate;
        this.gender = gender;
        this.alternativeId = alternativeId;
        this.guardianName = guardianName;
        this.guardianRelationship = guardianRelationship;
        this.guardianPhone = guardianPhone;
        this.requesterPhone = requesterPhone;
        this.requesterEmail = requesterEmail;
        this.department = department;
        this.municipality = municipality;
        this.address = address;
        this.emergencyName = emergencyName;
        this.emergencyPhone = emergencyPhone;
        this.nit = nit;
        this.billingName = billingName;
        this.billingAddress = billingAddress;
        this.requestReason = requestReason;
        this.privacyAccepted = privacyAccepted;
        this.privacyNoticeVersion = privacyNoticeVersion;
        this.privacyAcceptedAt = privacyAcceptedAt;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyPayloadHash = idempotencyPayloadHash;
    }

    public AppointmentRequest(UUID id, Patient patient, User requestedProfessional, Instant requestedAt,
                              AppointmentRequestStatus status, Instant createdAt, Instant updatedAt,
                              String requesterFullName, String requesterCui, String requesterPhone,
                              String requesterEmail, String requestReason,
                              UUID idempotencyKey, String idempotencyPayloadHash) {
        this(id, patient, requestedProfessional, requestedAt, status, createdAt, updatedAt,
                AppointmentRequestSource.PUBLIC, requesterFullName, requesterCui, null, null, null, null, null,
                null, requesterPhone, requesterEmail, null, null, null, null, null, null, null, null,
                requestReason, true, "1.0", createdAt, idempotencyKey, idempotencyPayloadHash);
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getRequestedProfessional() { return requestedProfessional; }
    public User getAssignedProfessional() { return assignedProfessional; }
    public Instant getRequestedAt() { return requestedAt; }
    public User getProposedProfessional() { return proposedProfessional; }
    public Instant getProposedAt() { return proposedAt; }
    public AppointmentRequestStatus getStatus() { return status; }
    public User getProcessedBy() { return processedBy; }
    public Appointment getAppointment() { return appointment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AppointmentRequestSource getSource() { return source; }
    public String getRequesterFullName() { return requesterFullName; }
    public String getRequesterCui() { return requesterCui; }
    public java.time.LocalDate getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
    public String getAlternativeId() { return alternativeId; }
    public String getGuardianName() { return guardianName; }
    public String getGuardianRelationship() { return guardianRelationship; }
    public String getGuardianPhone() { return guardianPhone; }
    public String getRequesterPhone() { return requesterPhone; }
    public String getRequesterEmail() { return requesterEmail; }
    public String getDepartment() { return department; }
    public String getMunicipality() { return municipality; }
    public String getAddress() { return address; }
    public String getEmergencyName() { return emergencyName; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public String getNit() { return nit; }
    public String getBillingName() { return billingName; }
    public String getBillingAddress() { return billingAddress; }
    public String getRequestReason() { return requestReason; }
    public boolean isPrivacyAccepted() { return privacyAccepted; }
    public boolean getPrivacyAccepted() { return privacyAccepted; }
    public String getPrivacyNoticeVersion() { return privacyNoticeVersion; }
    public Instant getPrivacyAcceptedAt() { return privacyAcceptedAt; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }

    public void setSource(AppointmentRequestSource source) {
        this.source = source;
    }

    public void linkPatient(Patient patient) {
        this.patient = patient;
    }

    public void assignProfessional(User professional, Instant now) {
        this.assignedProfessional = professional;
        this.updatedAt = now;
    }

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
