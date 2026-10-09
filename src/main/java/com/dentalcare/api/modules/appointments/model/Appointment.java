package com.dentalcare.api.modules.appointments.model;

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
@Table(name = "appointments")
public class Appointment {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "public_contact_name", length = 150)
    private String publicContactName;

    @Column(name = "public_contact_phone", length = 30)
    private String publicContactPhone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {}

    public Appointment(UUID id, Patient patient, User professional, Instant scheduledAt,
                       AppointmentStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patient = patient;
        this.professional = professional;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Appointment forPublicRequest(UUID id, String fullName, String phone, User professional,
                                                Instant scheduledAt, Instant now) {
        Appointment appointment = new Appointment(id, null, professional, scheduledAt,
                AppointmentStatus.SCHEDULED, now, now);
        appointment.publicContactName = fullName;
        appointment.publicContactPhone = phone;
        return appointment;
    }

    public UUID getId() { return id; }
    public Patient getPatient() { return patient; }
    public String getPublicContactName() { return publicContactName; }
    public String getPublicContactPhone() { return publicContactPhone; }
    public User getProfessional() { return professional; }
    public Instant getScheduledAt() { return scheduledAt; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void linkPatient(Patient patient, Instant now) { this.patient = patient; this.updatedAt = now; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Appointment that = (Appointment) object;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
