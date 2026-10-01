package com.dentalcare.api.modules.treatments.model;

import com.dentalcare.api.modules.patients.model.Patient;
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
@Table(name = "treatment_procedures")
public class TreatmentProcedure {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false, updatable = false)
    private TreatmentPlan treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_item_id", nullable = false, updatable = false)
    private TreatmentPlanItem treatmentPlanItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false, updatable = false)
    private User professional;

    @Column(name = "procedure_name", nullable = false, length = 200, updatable = false)
    private String procedureName;

    @Column(name = "tooth", length = 20, updatable = false)
    private String tooth;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Integer sequenceNumber;

    @Column(name = "clinical_observations", length = 4000)
    private String clinicalObservations;

    @Column(name = "completion_notes", length = 4000)
    private String completionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TreatmentProcedureStatus status;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TreatmentProcedure() {
    }

    public TreatmentProcedure(UUID id, TreatmentPlan treatmentPlan, TreatmentPlanItem treatmentPlanItem,
                              Patient patient, User professional, String procedureName, String tooth,
                              Integer sequenceNumber, String clinicalObservations,
                              TreatmentProcedureStatus status, Instant performedAt) {
        this.id = id;
        this.treatmentPlan = treatmentPlan;
        this.treatmentPlanItem = treatmentPlanItem;
        this.patient = patient;
        this.professional = professional;
        this.procedureName = procedureName;
        this.tooth = tooth;
        this.sequenceNumber = sequenceNumber;
        this.clinicalObservations = clinicalObservations;
        this.status = status;
        this.performedAt = performedAt;
    }

    public void complete(String completionNotes, Instant completedAt) {
        this.status = TreatmentProcedureStatus.COMPLETED;
        this.completionNotes = completionNotes;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public TreatmentPlan getTreatmentPlan() { return treatmentPlan; }
    public TreatmentPlanItem getTreatmentPlanItem() { return treatmentPlanItem; }
    public Patient getPatient() { return patient; }
    public User getProfessional() { return professional; }
    public String getProcedureName() { return procedureName; }
    public String getTooth() { return tooth; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public String getClinicalObservations() { return clinicalObservations; }
    public String getCompletionNotes() { return completionNotes; }
    public TreatmentProcedureStatus getStatus() { return status; }
    public Instant getPerformedAt() { return performedAt; }
    public Instant getCompletedAt() { return completedAt; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        TreatmentProcedure that = (TreatmentProcedure) object;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
