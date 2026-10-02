package com.dentalcare.api.modules.appointments.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_waiting_room_entries")
public class WaitingRoomEntry {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitingRoomStatus status;

    @Column(name = "arrived_at", nullable = false, updatable = false)
    private Instant arrivedAt;
    @Column(name = "waiting_at")
    private Instant waitingAt;
    @Column(name = "ready_at")
    private Instant readyAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_in_by", nullable = false)
    private User checkedInBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_updated_by", nullable = false)
    private User lastUpdatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WaitingRoomEntry() {}

    public WaitingRoomEntry(UUID id, Appointment appointment, User actor, Instant now) {
        this.id = id;
        this.appointment = appointment;
        this.status = WaitingRoomStatus.ARRIVED;
        this.arrivedAt = now;
        this.checkedInBy = actor;
        this.lastUpdatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public WaitingRoomStatus getStatus() { return status; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getWaitingAt() { return waitingAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getClosedAt() { return closedAt; }
    public User getCheckedInBy() { return checkedInBy; }
    public User getLastUpdatedBy() { return lastUpdatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void advanceTo(WaitingRoomStatus next, User actor, Instant now) {
        status = next;
        if (next == WaitingRoomStatus.WAITING) waitingAt = now;
        if (next == WaitingRoomStatus.READY) readyAt = now;
        lastUpdatedBy = actor;
        updatedAt = now;
    }

    public void close(User actor, Instant now) {
        status = WaitingRoomStatus.CLOSED;
        closedAt = now;
        lastUpdatedBy = actor;
        updatedAt = now;
    }
}
