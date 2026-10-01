package com.dentalcare.api.modules.clinicalrecords.model;

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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "clinical_evolution_notes")
public class ClinicalEvolutionNote {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attention_id", nullable = false)
    private ClinicalAttention attention;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "consultation_date", nullable = false)
    private LocalDate consultationDate;

    @Column(name = "procedure_summary", nullable = false, length = 300)
    private String procedureSummary;

    @Column(name = "note", nullable = false, length = 4000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClinicalEvolutionNote() {}

    public ClinicalEvolutionNote(UUID id, Patient patient, ClinicalAttention attention,
                                 User author, LocalDate consultationDate,
                                 String procedureSummary, String note, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.attention = attention;
        this.author = author;
        this.consultationDate = consultationDate;
        this.procedureSummary = procedureSummary;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public ClinicalAttention getAttention() { return attention; }
    public User getAuthor() { return author; }
    public LocalDate getConsultationDate() { return consultationDate; }
    public String getProcedureSummary() { return procedureSummary; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClinicalEvolutionNote that = (ClinicalEvolutionNote) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
