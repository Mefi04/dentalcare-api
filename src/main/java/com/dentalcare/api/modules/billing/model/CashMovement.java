package com.dentalcare.api.modules.billing.model;

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
@Table(name = "billing_cash_movements")
public class CashMovement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_shift_id", nullable = false, updatable = false)
    private CashShift cashShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private CashMovementType type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "concept", nullable = false, updatable = false, length = 200)
    private String concept;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CashMovement() {
    }

    public CashMovement(UUID id, CashShift cashShift, CashMovementType type, BigDecimal amount,
                        String concept, UUID createdByUserId, Instant createdAt) {
        this.id = id;
        this.cashShift = cashShift;
        this.type = type;
        this.amount = amount;
        this.concept = concept;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public CashShift getCashShift() { return cashShift; }
    public CashMovementType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getConcept() { return concept; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof CashMovement movement && Objects.equals(id, movement.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
