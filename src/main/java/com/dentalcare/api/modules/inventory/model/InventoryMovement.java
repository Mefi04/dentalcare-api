package com.dentalcare.api.modules.inventory.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false, updatable = false)
    private InventoryItem item;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private InventoryMovementType type;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "stock_before", nullable = false, updatable = false)
    private Integer stockBefore;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private Integer stockAfter;

    @Column(name = "available_before", updatable = false)
    private Integer availableBefore;

    @Column(name = "available_after", updatable = false)
    private Integer availableAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false, updatable = false)
    private User performedBy;

    @Column(name = "observation", updatable = false, length = 500)
    private String observation;

    @Column(name = "reference", updatable = false, length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(UUID id, InventoryItem item, InventoryMovementType type, Integer quantity,
                             Integer stockBefore, Integer stockAfter, Integer availableBefore,
                             Integer availableAfter, User performedBy, String observation,
                             String reference, Instant createdAt) {
        this.id = id;
        this.item = item;
        this.type = type;
        this.quantity = quantity;
        this.stockBefore = stockBefore;
        this.stockAfter = stockAfter;
        this.availableBefore = availableBefore;
        this.availableAfter = availableAfter;
        this.performedBy = performedBy;
        this.observation = observation;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public InventoryItem getItem() {
        return item;
    }

    public InventoryMovementType getType() {
        return type;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getStockBefore() {
        return stockBefore;
    }

    public Integer getStockAfter() {
        return stockAfter;
    }

    public Integer getAvailableBefore() {
        return availableBefore;
    }

    public Integer getAvailableAfter() {
        return availableAfter;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public String getObservation() {
        return observation;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventoryMovement that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
