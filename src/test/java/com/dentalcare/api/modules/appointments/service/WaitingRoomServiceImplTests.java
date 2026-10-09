package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.mapper.WaitingRoomMapper;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.*;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingRoomServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    @Mock WaitingRoomRepository waitingRoom;
    @Mock AppointmentRepository appointments;
    @Mock UserRepository users;

    private WaitingRoomServiceImpl service;
    private User actor;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        service = new WaitingRoomServiceImpl(waitingRoom, appointments, users, new WaitingRoomMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        actor = new User();
        actor.setId(UUID.randomUUID());
        appointment = appointment(NOW.plusSeconds(3600), AppointmentStatus.SCHEDULED);
    }

    @Test
    void checksInOnlyScheduledAppointmentFromGuatemalaClinicDay() {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(waitingRoom.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.checkIn(actor.getId(), appointment.getId());

        assertThat(result.status()).isEqualTo(WaitingRoomStatus.ARRIVED);
        assertThat(result.arrivedAt()).isEqualTo(NOW);
    }

    @Test
    void guestAppointmentRequiresInPersonPatientLinkBeforeCheckIn() {
        Appointment guest = Appointment.forPublicRequest(UUID.randomUUID(), "Visitante", "5555-0198",
                appointment.getProfessional(), NOW.plusSeconds(3600), NOW);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(appointments.findById(guest.getId())).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> service.checkIn(actor.getId(), guest.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_RECORD_LINK_REQUIRED");
        verifyNoInteractions(waitingRoom);
    }

    @Test
    void rejectsDuplicateCancelledAndDifferentClinicDayCheckIn() {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(waitingRoom.existsByAppointment_Id(appointment.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.checkIn(actor.getId(), appointment.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already checked in");

        Appointment cancelled = appointment(NOW.plusSeconds(3600), AppointmentStatus.CANCELLED);
        when(appointments.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.checkIn(actor.getId(), cancelled.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("scheduled");

        Appointment tomorrow = appointment(NOW.plus(Duration.ofDays(1)), AppointmentStatus.SCHEDULED);
        when(appointments.findById(tomorrow.getId())).thenReturn(Optional.of(tomorrow));
        assertThatThrownBy(() -> service.checkIn(actor.getId(), tomorrow.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("current clinic day");
    }

    @Test
    void advancesArrivalToWaitingToReadyAndRejectsInvalidTransition() {
        WaitingRoomEntry entry = new WaitingRoomEntry(UUID.randomUUID(), appointment, actor, NOW);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(waitingRoom.findByAppointmentIdForUpdate(appointment.getId())).thenReturn(Optional.of(entry));
        when(waitingRoom.saveAndFlush(entry)).thenReturn(entry);

        assertThat(service.advance(actor.getId(), appointment.getId(), WaitingRoomStatus.WAITING).status())
                .isEqualTo(WaitingRoomStatus.WAITING);
        assertThat(service.advance(actor.getId(), appointment.getId(), WaitingRoomStatus.READY).status())
                .isEqualTo(WaitingRoomStatus.READY);
        assertThatThrownBy(() -> service.advance(actor.getId(), appointment.getId(), WaitingRoomStatus.WAITING))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void closesExistingOperationalEntryWhenAppointmentEnds() {
        WaitingRoomEntry entry = new WaitingRoomEntry(UUID.randomUUID(), appointment, actor, NOW);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(waitingRoom.findByAppointmentIdForUpdate(appointment.getId())).thenReturn(Optional.of(entry));

        service.closeForAppointment(actor.getId(), appointment.getId());

        assertThat(entry.getStatus()).isEqualTo(WaitingRoomStatus.CLOSED);
        assertThat(entry.getClosedAt()).isEqualTo(NOW);
        verify(waitingRoom).saveAndFlush(entry);
    }

    private Appointment appointment(Instant scheduledAt, AppointmentStatus status) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-001");
        patient.setName("Ana Pérez");
        patient.setPhone("5555-0101");
        User dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setFullName("Dra. Ruiz");
        return new Appointment(UUID.randomUUID(), patient, dentist, scheduledAt, status, NOW, NOW);
    }
}
