package com.dentalcare.api.modules.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "billing_charge_adjustments")
public class ChargeAdjustment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private ChargeAdjustmentType type;

    @Column(name = "amount", updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(name = "authorized_by_user_id", nullable = false, updatable = false)
    private UUID authorizedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChargeAdjustment() {
    }

    public ChargeAdjustment(UUID id, UUID chargeId, UUID patientId, ChargeAdjustmentType type, BigDecimal amount,
                            String reason, UUID requestedByUserId, UUID authorizedByUserId, Instant createdAt) {
        this.id = id;
        this.chargeId = chargeId;
        this.patientId = patientId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.requestedByUserId = requestedByUserId;
        this.authorizedByUserId = authorizedByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getChargeId() { return chargeId; }
    public UUID getPatientId() { return patientId; }
    public ChargeAdjustmentType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getAuthorizedByUserId() { return authorizedByUserId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ChargeAdjustment adjustment && Objects.equals(id, adjustment.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
