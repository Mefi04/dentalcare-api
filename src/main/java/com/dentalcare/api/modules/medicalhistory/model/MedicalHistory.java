package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.patients.model.Patient;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "medical_histories")
public class MedicalHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, unique = true, updatable = false)
    private Patient patient;

    @ElementCollection
    @CollectionTable(name = "medical_history_allergies",
            joinColumns = @JoinColumn(name = "medical_history_id"))
    @Column(name = "description", nullable = false, length = 200)
    private Set<String> allergies = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "medical_history_medications",
            joinColumns = @JoinColumn(name = "medical_history_id"))
    @Column(name = "description", nullable = false, length = 200)
    private Set<String> currentMedications = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "medical_history_conditions",
            joinColumns = @JoinColumn(name = "medical_history_id"))
    @Column(name = "description", nullable = false, length = 200)
    private Set<String> relevantConditions = new LinkedHashSet<>();

    @Column(name = "observations", length = 4000)
    private String observations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private MedicalHistoryVersion currentVersion;

    protected MedicalHistory() {
    }

    public MedicalHistory(UUID id, Patient patient, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Set<String> getAllergies() {
        return allergies;
    }

    public Set<String> getCurrentMedications() {
        return currentMedications;
    }

    public Set<String> getRelevantConditions() {
        return relevantConditions;
    }

    public String getObservations() {
        return observations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public MedicalHistoryVersion getCurrentVersion() {
        return currentVersion;
    }

    public void replaceAllergies(Collection<String> values) {
        allergies.clear();
        allergies.addAll(values);
    }

    public void replaceCurrentMedications(Collection<String> values) {
        currentMedications.clear();
        currentMedications.addAll(values);
    }

    public void replaceRelevantConditions(Collection<String> values) {
        relevantConditions.clear();
        relevantConditions.addAll(values);
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setCurrentVersion(MedicalHistoryVersion currentVersion) {
        this.currentVersion = currentVersion;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof MedicalHistory history && Objects.equals(id, history.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
