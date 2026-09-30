package com.dentalcare.api.modules.billing.model;

import com.dentalcare.api.modules.patients.model.Patient;
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
@Table(name = "billing_charges")
public class Charge {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "concept", nullable = false, updatable = false, length = 200)
    private String concept;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Charge() {
    }

    public Charge(UUID id, Patient patient, String concept, BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.concept = concept;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public String getConcept() { return concept; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Charge charge && Objects.equals(id, charge.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
