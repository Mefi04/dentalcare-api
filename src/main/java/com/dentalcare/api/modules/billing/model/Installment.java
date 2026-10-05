package com.dentalcare.api.modules.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "billing_installments")
public class Installment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private PaymentPlan plan;

    @Column(name = "number", nullable = false, updatable = false)
    private int number;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    protected Installment() {
    }

    public Installment(UUID id, int number, BigDecimal amount, LocalDate dueDate) {
        this.id = id;
        this.number = number;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    void attachTo(PaymentPlan plan) {
        this.plan = plan;
    }

    public UUID getId() { return id; }
    public PaymentPlan getPlan() { return plan; }
    public int getNumber() { return number; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Installment installment && Objects.equals(id, installment.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
