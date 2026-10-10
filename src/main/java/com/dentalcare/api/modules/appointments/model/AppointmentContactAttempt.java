package com.dentalcare.api.modules.appointments.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_contact_attempts")
public class AppointmentContactAttempt {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_request_id", nullable = false)
    private AppointmentRequest appointmentRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentContactResult result;

    @Column(length = 500)
    private String observation;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected AppointmentContactAttempt() { }

    public AppointmentContactAttempt(UUID id, AppointmentRequest appointmentRequest, User actor,
                                     Instant attemptedAt, AppointmentContactResult result,
                                     String observation, Instant nextAttemptAt) {
        this.id = id;
        this.appointmentRequest = appointmentRequest;
        this.actor = actor;
        this.attemptedAt = attemptedAt;
        this.result = result;
        this.observation = observation;
        this.nextAttemptAt = nextAttemptAt;
    }

    public UUID getId() { return id; }
    public AppointmentRequest getAppointmentRequest() { return appointmentRequest; }
    public User getActor() { return actor; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public AppointmentContactResult getResult() { return result; }
    public String getObservation() { return observation; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
