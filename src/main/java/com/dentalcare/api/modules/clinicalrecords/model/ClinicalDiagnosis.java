package com.dentalcare.api.modules.clinicalrecords.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
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
@Table(name = "clinical_diagnoses")
public class ClinicalDiagnosis {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attention_id", nullable = false)
    private ClinicalAttention attention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_plan_id")
    private TreatmentPlan treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DiagnosisType type;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClinicalDiagnosis() {}

    public ClinicalDiagnosis(UUID id, Patient patient, ClinicalAttention attention,
                             TreatmentPlan treatmentPlan, User author,
                             DiagnosisType type, String description, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.attention = attention;
        this.treatmentPlan = treatmentPlan;
        this.author = author;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public ClinicalAttention getAttention() { return attention; }
    public TreatmentPlan getTreatmentPlan() { return treatmentPlan; }
    public User getAuthor() { return author; }
    public DiagnosisType getType() { return type; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClinicalDiagnosis that = (ClinicalDiagnosis) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
