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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "treatment_plans")
public class TreatmentPlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "observations", length = 4000)
    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TreatmentPlanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "treatmentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TreatmentPlanItem> items = new ArrayList<>();

    protected TreatmentPlan() {
    }

    public TreatmentPlan(UUID id, Patient patient, User professional, String name, String observations,
                         TreatmentPlanStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.professional = professional;
        this.name = name;
        this.observations = observations;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String name, String observations, User professional, Instant updatedAt) {
        this.name = name;
        this.observations = observations;
        this.professional = professional;
        this.updatedAt = updatedAt;
    }

    public void replaceItems(Collection<TreatmentPlanItem> replacements) {
        clearItems();
        replacements.forEach(this::addItem);
    }

    public void clearItems() {
        items.clear();
    }

    public void addItem(TreatmentPlanItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void approve(Instant approvedAt) {
        this.status = TreatmentPlanStatus.APPROVED;
        this.approvedAt = approvedAt;
        this.updatedAt = approvedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public User getProfessional() { return professional; }
    public String getName() { return name; }
    public String getObservations() { return observations; }
    public TreatmentPlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public List<TreatmentPlanItem> getItems() { return items; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        TreatmentPlan that = (TreatmentPlan) object;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
