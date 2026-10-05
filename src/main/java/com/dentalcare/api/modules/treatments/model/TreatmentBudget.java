package com.dentalcare.api.modules.treatments.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "treatment_budgets")
public class TreatmentBudget {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false, updatable = false)
    private TreatmentPlan treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    @Column(name = "plan_updated_at", nullable = false, updatable = false)
    private Instant planUpdatedAt;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TreatmentBudgetStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false, updatable = false)
    private User generatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_decision", nullable = false, length = 20)
    private PatientBudgetDecision patientDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_decided_by")
    private User patientDecidedBy;

    @Column(name = "patient_decided_at")
    private Instant patientDecidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "treatmentBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TreatmentBudgetItem> items = new ArrayList<>();

    protected TreatmentBudget() {
    }

    public TreatmentBudget(UUID id, TreatmentPlan treatmentPlan, Patient patient, Integer version,
                           Instant planUpdatedAt, BigDecimal subtotal, BigDecimal total,
                           User generatedBy, Instant createdAt) {
        this.id = id;
        this.treatmentPlan = treatmentPlan;
        this.patient = patient;
        this.version = version;
        this.planUpdatedAt = planUpdatedAt;
        this.subtotal = subtotal;
        this.total = total;
        this.status = TreatmentBudgetStatus.PENDING;
        this.patientDecision = PatientBudgetDecision.PENDING;
        this.generatedBy = generatedBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void replaceItems(Collection<TreatmentBudgetItem> replacements) {
        items.clear();
        replacements.forEach(item -> {
            item.attachTo(this);
            items.add(item);
        });
    }

    public void approve(User actor, Instant at) {
        status = TreatmentBudgetStatus.APPROVED;
        decidedBy = actor;
        decidedAt = at;
        updatedAt = at;
    }

    public void reject(User actor, Instant at) {
        status = TreatmentBudgetStatus.REJECTED;
        decidedBy = actor;
        decidedAt = at;
        updatedAt = at;
    }

    public void acceptByPatient(User actor, Instant at) {
        patientDecision = PatientBudgetDecision.ACCEPTED;
        patientDecidedBy = actor;
        patientDecidedAt = at;
        updatedAt = at;
    }

    public void rejectByPatient(User actor, Instant at) {
        patientDecision = PatientBudgetDecision.REJECTED;
        patientDecidedBy = actor;
        patientDecidedAt = at;
        updatedAt = at;
    }

    public UUID getId() { return id; }
    public TreatmentPlan getTreatmentPlan() { return treatmentPlan; }
    public Patient getPatient() { return patient; }
    public Integer getVersion() { return version; }
    public Instant getPlanUpdatedAt() { return planUpdatedAt; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public TreatmentBudgetStatus getStatus() { return status; }
    public User getGeneratedBy() { return generatedBy; }
    public User getDecidedBy() { return decidedBy; }
    public PatientBudgetDecision getPatientDecision() { return patientDecision; }
    public User getPatientDecidedBy() { return patientDecidedBy; }
    public Instant getPatientDecidedAt() { return patientDecidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public List<TreatmentBudgetItem> getItems() { return items; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return Objects.equals(id, ((TreatmentBudget) object).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
