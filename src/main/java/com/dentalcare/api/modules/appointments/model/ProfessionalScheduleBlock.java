package com.dentalcare.api.modules.appointments.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_schedule_blocks")
public class ProfessionalScheduleBlock {
    @Id private UUID id;
    @Column(name = "professional_id", nullable = false) private UUID professionalId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(length = 150) private String reason;
    protected ProfessionalScheduleBlock() { }
    public ProfessionalScheduleBlock(UUID id, UUID professionalId, Instant startsAt,
            Instant endsAt, String reason) {
        this.id = id; this.professionalId = professionalId; this.startsAt = startsAt;
        this.endsAt = endsAt; this.reason = reason;
    }
    public UUID getId() { return id; }
    public String getReason() { return reason; }
    public UUID getProfessionalId() { return professionalId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
