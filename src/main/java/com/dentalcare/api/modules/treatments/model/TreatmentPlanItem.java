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
@Table(name = "treatment_plan_items")
public class TreatmentPlanItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false, updatable = false)
    private TreatmentPlan treatmentPlan;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tooth", length = 20)
    private String tooth;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "position", nullable = false)
    private Integer position;

    protected TreatmentPlanItem() {
    }

    public TreatmentPlanItem(UUID id, String name, String tooth, Integer quantity,
                             BigDecimal unitPrice, Integer position) {
        this.id = id;
        this.name = name;
        this.tooth = tooth;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.position = position;
    }

    void attachTo(TreatmentPlan treatmentPlan) {
        this.treatmentPlan = treatmentPlan;
    }

    public UUID getId() { return id; }
    public TreatmentPlan getTreatmentPlan() { return treatmentPlan; }
    public String getName() { return name; }
    public String getTooth() { return tooth; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getPosition() { return position; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        TreatmentPlanItem that = (TreatmentPlanItem) object;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
