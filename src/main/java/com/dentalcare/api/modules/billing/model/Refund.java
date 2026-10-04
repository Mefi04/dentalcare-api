package com.dentalcare.api.modules.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "billing_refunds")
public class Refund {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(name = "authorized_by_user_id", nullable = false, updatable = false)
    private UUID authorizedByUserId;

    @Column(name = "cash_shift_id", updatable = false)
    private UUID cashShiftId;

    @Column(name = "idempotency_key", updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Refund() {
    }

    public Refund(UUID id, UUID paymentId, UUID patientId, BigDecimal amount, String reason,
                  UUID requestedByUserId, UUID authorizedByUserId, UUID cashShiftId, String idempotencyKey,
                  Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.patientId = patientId;
        this.amount = amount;
        this.reason = reason;
        this.requestedByUserId = requestedByUserId;
        this.authorizedByUserId = authorizedByUserId;
        this.cashShiftId = cashShiftId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getPatientId() { return patientId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getAuthorizedByUserId() { return authorizedByUserId; }
    public UUID getCashShiftId() { return cashShiftId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Refund refund && Objects.equals(id, refund.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
