package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.WaitingRoomEntryResponse;
import com.dentalcare.api.modules.appointments.model.WaitingRoomStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface WaitingRoomService {
    Page<WaitingRoomEntryResponse> findAll(LocalDate date, WaitingRoomStatus status,
                                           UUID professionalId, int page, int size);
    WaitingRoomEntryResponse findByAppointmentId(UUID appointmentId);
    WaitingRoomEntryResponse checkIn(UUID actorId, UUID appointmentId);
    WaitingRoomEntryResponse advance(UUID actorId, UUID appointmentId, WaitingRoomStatus status);
    void closeForAppointment(UUID actorId, UUID appointmentId);
}
