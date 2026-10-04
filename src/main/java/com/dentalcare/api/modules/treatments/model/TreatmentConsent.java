package com.dentalcare.api.modules.treatments.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "treatment_consents")
public class TreatmentConsent {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false, updatable = false)
    private TreatmentPlan treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_budget_id", updatable = false)
    private TreatmentBudget treatmentBudget;

    @Column(name = "document_version", nullable = false, length = 80, updatable = false)
    private String documentVersion;

    @Column(name = "consent_text", nullable = false, length = 20000, updatable = false)
    private String consentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TreatmentConsentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prepared_by", nullable = false, updatable = false)
    private User preparedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TreatmentConsent() {
    }

    public TreatmentConsent(UUID id, TreatmentPlan plan, Patient patient, TreatmentBudget budget,
                            String documentVersion, String consentText, User preparedBy, Instant createdAt) {
        this.id = id;
        this.treatmentPlan = plan;
        this.patient = patient;
        this.treatmentBudget = budget;
        this.documentVersion = documentVersion;
        this.consentText = consentText;
        this.status = TreatmentConsentStatus.PENDING;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void accept(User actor, Instant at) {
        status = TreatmentConsentStatus.ACCEPTED;
        acceptedBy = actor;
        acceptedAt = at;
        updatedAt = at;
    }

    public void revoke(User actor, Instant at) {
        status = TreatmentConsentStatus.REVOKED;
        revokedBy = actor;
        revokedAt = at;
        updatedAt = at;
    }

    public UUID getId() { return id; }
    public TreatmentPlan getTreatmentPlan() { return treatmentPlan; }
    public Patient getPatient() { return patient; }
    public TreatmentBudget getTreatmentBudget() { return treatmentBudget; }
    public String getDocumentVersion() { return documentVersion; }
    public String getConsentText() { return consentText; }
    public TreatmentConsentStatus getStatus() { return status; }
    public User getPreparedBy() { return preparedBy; }
    public User getAcceptedBy() { return acceptedBy; }
    public User getRevokedBy() { return revokedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return Objects.equals(id, ((TreatmentConsent) object).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
