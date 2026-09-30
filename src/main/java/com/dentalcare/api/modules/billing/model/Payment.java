package com.dentalcare.api.modules.billing.model;

import com.dentalcare.api.modules.patients.model.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "billing_payments")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", updatable = false)
    private Charge charge;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 30)
    private PaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(UUID id, Patient patient, Charge charge, PaymentKind kind, PaymentMethod method,
                   BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.charge = charge;
        this.kind = kind;
        this.method = method;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public Charge getCharge() { return charge; }
    public PaymentKind getKind() { return kind; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Payment payment && Objects.equals(id, payment.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
