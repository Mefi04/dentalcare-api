package com.dentalcare.api.modules.treatments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "treatment_budget_items")
public class TreatmentBudgetItem {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_budget_id", nullable = false, updatable = false)
    private TreatmentBudget treatmentBudget;

    @Column(name = "treatment_plan_item_id", nullable = false, updatable = false)
    private UUID treatmentPlanItemId;

    @Column(name = "name", nullable = false, length = 200, updatable = false)
    private String name;

    @Column(name = "tooth", length = 20, updatable = false)
    private String tooth;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "position", nullable = false, updatable = false)
    private Integer position;

    protected TreatmentBudgetItem() {
    }

    public TreatmentBudgetItem(UUID id, UUID treatmentPlanItemId, String name, String tooth,
                               Integer quantity, BigDecimal unitPrice, BigDecimal subtotal,
                               Integer position) {
        this.id = id;
        this.treatmentPlanItemId = treatmentPlanItemId;
        this.name = name;
        this.tooth = tooth;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.position = position;
    }

    void attachTo(TreatmentBudget budget) { this.treatmentBudget = budget; }

    public UUID getId() { return id; }
    public TreatmentBudget getTreatmentBudget() { return treatmentBudget; }
    public UUID getTreatmentPlanItemId() { return treatmentPlanItemId; }
    public String getName() { return name; }
    public String getTooth() { return tooth; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
    public Integer getPosition() { return position; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return Objects.equals(id, ((TreatmentBudgetItem) object).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
