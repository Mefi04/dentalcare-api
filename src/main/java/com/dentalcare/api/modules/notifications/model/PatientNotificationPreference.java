package com.dentalcare.api.modules.notifications.model;

import com.dentalcare.api.modules.patients.model.Patient;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_notification_preferences")
public class PatientNotificationPreference {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    private Patient patient;

    @Column(name = "appointments_enabled", nullable = false)
    private boolean appointmentsEnabled;
    @Column(name = "payments_enabled", nullable = false)
    private boolean paymentsEnabled;
    @Column(name = "medications_enabled", nullable = false)
    private boolean medicationsEnabled;
    @Column(name = "clinic_updates_enabled", nullable = false)
    private boolean clinicUpdatesEnabled;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PatientNotificationPreference() {}

    public PatientNotificationPreference(UUID id, Patient patient, boolean appointmentsEnabled,
                                         boolean paymentsEnabled, boolean medicationsEnabled,
                                         boolean clinicUpdatesEnabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.appointmentsEnabled = appointmentsEnabled;
        this.paymentsEnabled = paymentsEnabled;
        this.medicationsEnabled = medicationsEnabled;
        this.clinicUpdatesEnabled = clinicUpdatesEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public boolean isAppointmentsEnabled() { return appointmentsEnabled; }
    public boolean isPaymentsEnabled() { return paymentsEnabled; }
    public boolean isMedicationsEnabled() { return medicationsEnabled; }
    public boolean isClinicUpdatesEnabled() { return clinicUpdatesEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean appointmentsEnabled, boolean paymentsEnabled,
                       boolean medicationsEnabled, boolean clinicUpdatesEnabled, Instant updatedAt) {
        this.appointmentsEnabled = appointmentsEnabled;
        this.paymentsEnabled = paymentsEnabled;
        this.medicationsEnabled = medicationsEnabled;
        this.clinicUpdatesEnabled = clinicUpdatesEnabled;
        this.updatedAt = updatedAt;
    }
}
