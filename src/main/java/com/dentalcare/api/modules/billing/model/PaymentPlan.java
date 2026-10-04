package com.dentalcare.api.modules.billing.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "billing_payment_plans")
public class PaymentPlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "installments_count", nullable = false, updatable = false)
    private int installmentsCount;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "baseline_paid", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baselinePaid;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentPlanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("number ASC")
    private List<Installment> installments = new ArrayList<>();

    protected PaymentPlan() {
    }

    public PaymentPlan(UUID id, UUID chargeId, UUID patientId, int installmentsCount, BigDecimal totalAmount,
                       BigDecimal baselinePaid, LocalDate firstDueDate, UUID createdByUserId, Instant createdAt) {
        this.id = id;
        this.chargeId = chargeId;
        this.patientId = patientId;
        this.installmentsCount = installmentsCount;
        this.totalAmount = totalAmount;
        this.baselinePaid = baselinePaid;
        this.firstDueDate = firstDueDate;
        this.status = PaymentPlanStatus.ACTIVE;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public void addInstallment(Installment installment) {
        installment.attachTo(this);
        installments.add(installment);
    }

    public void cancel(UUID cancelledByUserId, String cancelReason, Instant cancelledAt) {
        this.status = PaymentPlanStatus.CANCELLED;
        this.cancelledByUserId = cancelledByUserId;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
    }

    public UUID getId() { return id; }
    public UUID getChargeId() { return chargeId; }
    public UUID getPatientId() { return patientId; }
    public int getInstallmentsCount() { return installmentsCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getBaselinePaid() { return baselinePaid; }
    public LocalDate getFirstDueDate() { return firstDueDate; }
    public PaymentPlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCancelledByUserId() { return cancelledByUserId; }
    public String getCancelReason() { return cancelReason; }
    public List<Installment> getInstallments() { return installments; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof PaymentPlan plan && Objects.equals(id, plan.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
