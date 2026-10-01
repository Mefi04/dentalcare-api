package com.dentalcare.api.modules.clinicalrecords.model;

import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "clinical_attentions")
public class ClinicalAttention {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "clinical_notes", nullable = false, length = 4000)
    private String clinicalNotes;

    @Column(name = "next_steps", length = 1000)
    private String nextSteps;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicalAttention() {}

    public ClinicalAttention(UUID id, Patient patient, User professional, Appointment appointment,
                             String reason, String clinicalNotes, String nextSteps,
                             Instant occurredAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.professional = professional;
        this.appointment = appointment;
        this.reason = reason;
        this.clinicalNotes = clinicalNotes;
        this.nextSteps = nextSteps;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getProfessional() { return professional; }
    public Appointment getAppointment() { return appointment; }
    public String getReason() { return reason; }
    public String getClinicalNotes() { return clinicalNotes; }
    public String getNextSteps() { return nextSteps; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClinicalAttention that = (ClinicalAttention) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
