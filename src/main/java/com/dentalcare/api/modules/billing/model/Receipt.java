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
@Table(name = "billing_receipts")
public class Receipt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, updatable = false)
    private Long receiptNumber;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "concept", nullable = false, updatable = false, length = 200)
    private String concept;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "issued_by_user_id", nullable = false, updatable = false)
    private UUID issuedByUserId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    protected Receipt() {
    }

    public Receipt(UUID id, long receiptNumber, UUID paymentId, UUID patientId, String concept,
                   BigDecimal amount, PaymentMethod method, UUID issuedByUserId, Instant issuedAt) {
        this.id = id;
        this.receiptNumber = receiptNumber;
        this.paymentId = paymentId;
        this.patientId = patientId;
        this.concept = concept;
        this.amount = amount;
        this.method = method;
        this.status = ReceiptStatus.ISSUED;
        this.issuedByUserId = issuedByUserId;
        this.issuedAt = issuedAt;
    }

    public UUID getId() { return id; }
    public Long getReceiptNumber() { return receiptNumber; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getPatientId() { return patientId; }
    public String getConcept() { return concept; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public ReceiptStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public UUID getIssuedByUserId() { return issuedByUserId; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }

    public void voidReceipt(Instant voidedAt, String reason) {
        this.status = ReceiptStatus.VOID;
        this.voidedAt = voidedAt;
        this.voidReason = reason;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Receipt receipt && Objects.equals(id, receipt.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
