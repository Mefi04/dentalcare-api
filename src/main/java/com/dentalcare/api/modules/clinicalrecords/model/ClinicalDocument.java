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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "clinical_documents")
public class ClinicalDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ClinicalDocumentType type;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ClinicalDocument() {}

    public ClinicalDocument(UUID id, Patient patient, User author, String title,
                            ClinicalDocumentType type, String description,
                            LocalDate documentDate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.author = author;
        this.title = title;
        this.type = type;
        this.description = description;
        this.documentDate = documentDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public ClinicalDocumentType getType() { return type; }
    public String getDescription() { return description; }
    public LocalDate getDocumentDate() { return documentDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setType(ClinicalDocumentType type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
    public void setDocumentDate(LocalDate documentDate) { this.documentDate = documentDate; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClinicalDocument that = (ClinicalDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
