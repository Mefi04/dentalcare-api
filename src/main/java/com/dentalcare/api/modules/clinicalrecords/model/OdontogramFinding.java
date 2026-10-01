package com.dentalcare.api.modules.clinicalrecords.model;

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
@Table(name = "odontogram_findings")
public class OdontogramFinding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attention_id")
    private ClinicalAttention attention;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "dentition", nullable = false, length = 20)
    private DentitionType dentition;

    @Column(name = "tooth_code", nullable = false, length = 10)
    private String toothCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface", length = 20)
    private ToothSurface surface;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding", nullable = false, length = 30)
    private ToothFinding finding;

    @Column(name = "observation", length = 500)
    private String observation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OdontogramFinding() {}

    public OdontogramFinding(UUID id, Patient patient, ClinicalAttention attention,
                             User author, DentitionType dentition, String toothCode,
                             ToothSurface surface, ToothFinding finding,
                             String observation, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.attention = attention;
        this.author = author;
        this.dentition = dentition;
        this.toothCode = toothCode;
        this.surface = surface;
        this.finding = finding;
        this.observation = observation;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public ClinicalAttention getAttention() { return attention; }
    public User getAuthor() { return author; }
    public DentitionType getDentition() { return dentition; }
    public String getToothCode() { return toothCode; }
    public ToothSurface getSurface() { return surface; }
    public ToothFinding getFinding() { return finding; }
    public String getObservation() { return observation; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OdontogramFinding that = (OdontogramFinding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
