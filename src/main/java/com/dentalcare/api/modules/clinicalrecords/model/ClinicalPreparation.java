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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "clinical_preparations")
public class ClinicalPreparation {

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
    @JoinColumn(name = "prepared_by_id", nullable = false)
    private User preparedBy;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "observations", length = 2000)
    private String observations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicalPreparation() {}

    public ClinicalPreparation(UUID id, Patient patient, ClinicalAttention attention,
                               User preparedBy, String bloodPressure, Integer heartRate,
                               BigDecimal temperature, BigDecimal weight, String observations,
                               Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.attention = attention;
        this.preparedBy = preparedBy;
        this.bloodPressure = bloodPressure;
        this.heartRate = heartRate;
        this.temperature = temperature;
        this.weight = weight;
        this.observations = observations;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public ClinicalAttention getAttention() { return attention; }
    public User getPreparedBy() { return preparedBy; }
    public String getBloodPressure() { return bloodPressure; }
    public Integer getHeartRate() { return heartRate; }
    public BigDecimal getTemperature() { return temperature; }
    public BigDecimal getWeight() { return weight; }
    public String getObservations() { return observations; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAttention(ClinicalAttention attention) {
        this.attention = attention;
    }

    public void setBloodPressure(String bloodPressure) {
        this.bloodPressure = bloodPressure;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClinicalPreparation that = (ClinicalPreparation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
