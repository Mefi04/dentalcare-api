package com.dentalcare.api.modules.prescriptions.model;

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
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
public class Prescription {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false, updatable = false)
    private User professional;

    @Column(nullable = false, length = 200)
    private String medication;

    @Column(nullable = false, length = 150)
    private String presentation;

    @Column(nullable = false, length = 150)
    private String dosage;

    @Column(nullable = false, length = 150)
    private String frequency;

    @Column(nullable = false, length = 150)
    private String duration;

    @Column(length = 2000)
    private String instructions;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrescriptionStatus status;

    protected Prescription() {
    }

    public Prescription(UUID id, Patient patient, User professional, String medication, String presentation,
                        String dosage, String frequency, String duration, String instructions,
                        Instant issuedAt, PrescriptionStatus status) {
        this.id = id;
        this.patient = patient;
        this.professional = professional;
        this.medication = medication;
        this.presentation = presentation;
        this.dosage = dosage;
        this.frequency = frequency;
        this.duration = duration;
        this.instructions = instructions;
        this.issuedAt = issuedAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getProfessional() {
        return professional;
    }

    public String getMedication() {
        return medication;
    }

    public String getPresentation() {
        return presentation;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getDuration() {
        return duration;
    }

    public String getInstructions() {
        return instructions;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }
}
