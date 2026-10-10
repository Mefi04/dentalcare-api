package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "professional_work_intervals")
public class ProfessionalWorkInterval {
    @Id private UUID id;
    @Column(name = "professional_id", nullable = false) private UUID professionalId;
    @Column(name = "day_of_week", nullable = false) private int dayOfWeek;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(nullable = false) private boolean active;
    protected ProfessionalWorkInterval() { }
    public ProfessionalWorkInterval(UUID id, UUID professionalId, int dayOfWeek,
            LocalTime startTime, LocalTime endTime) {
        this.id = id; this.professionalId = professionalId; this.dayOfWeek = dayOfWeek;
        this.startTime = startTime; this.endTime = endTime; this.active = true;
    }
    public UUID getId() { return id; }
    public UUID getProfessionalId() { return professionalId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public boolean isActive() { return active; }
}
