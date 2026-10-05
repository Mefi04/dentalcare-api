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
@Table(name = "billing_cash_shifts")
public class CashShift {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CashShiftStatus status;

    @Column(name = "opening_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "counted_amount", precision = 12, scale = 2)
    private BigDecimal countedAmount;

    @Column(name = "difference", precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(name = "opening_notes", updatable = false, length = 500)
    private String openingNotes;

    @Column(name = "closing_notes", length = 500)
    private String closingNotes;

    protected CashShift() {
    }

    public CashShift(UUID id, UUID userId, BigDecimal openingAmount, String openingNotes, Instant openedAt) {
        this.id = id;
        this.userId = userId;
        this.status = CashShiftStatus.OPEN;
        this.openingAmount = openingAmount;
        this.openingNotes = openingNotes;
        this.openedAt = openedAt;
    }

    public void close(BigDecimal expectedAmount, BigDecimal countedAmount, BigDecimal difference,
                      String closingNotes, Instant closedAt) {
        this.status = CashShiftStatus.CLOSED;
        this.expectedAmount = expectedAmount;
        this.countedAmount = countedAmount;
        this.difference = difference;
        this.closingNotes = closingNotes;
        this.closedAt = closedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public CashShiftStatus getStatus() { return status; }
    public BigDecimal getOpeningAmount() { return openingAmount; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getCountedAmount() { return countedAmount; }
    public BigDecimal getDifference() { return difference; }
    public String getOpeningNotes() { return openingNotes; }
    public String getClosingNotes() { return closingNotes; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof CashShift shift && Objects.equals(id, shift.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
