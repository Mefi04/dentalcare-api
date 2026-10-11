package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.model.SlotStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAvailabilityServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-19T06:00:00Z"); // Monday early morning

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private AppointmentRequestRepository requests;

    @Mock
    private ProfessionalPublicProfileRepository profiles;

    @Mock
    private UserRepository users;

    private AppointmentAvailabilityServiceImpl service;
    private User dentist;
    private ProfessionalPublicProfile profile;

    @BeforeEach
    void setUp() {
        service = new AppointmentAvailabilityServiceImpl(
                appointments,
                requests,
                profiles,
                users,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setFullName("Dr. Roberto Silva");
        dentist.setStatus(UserStatus.ACTIVE);
        Role dentistRole = new Role();
        dentistRole.setCode("DENTIST");
        dentistRole.setActive(true);
        dentist.setRoles(Set.of(dentistRole));

        profile = new ProfessionalPublicProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(dentist);
        profile.setPublicVisible(true);
        profile.setServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY);
    }

    @Test
    void returnsWeekdaySlotsWithAvailableStatusWhenNoBookings() {
        LocalDate tuesday = LocalDate.parse("2026-10-20");
        when(profiles.findPubliclyVisibleByServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY))
                .thenReturn(List.of(profile));
        when(appointments.findScheduledBetween(any(), any())).thenReturn(List.of());
        when(requests.findActiveRequestsBetween(any(), any())).thenReturn(List.of());

        AppointmentAvailabilityResponse response = service.getPublicAvailability(
                tuesday, null, ProfessionalServiceCode.GENERAL_DENTISTRY);

        assertThat(response.date()).isEqualTo(tuesday);
        assertThat(response.slots()).isNotEmpty();
        assertThat(response.slots().get(0).status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(response.slots().get(0).availableCapacity()).isEqualTo(1);
        assertThat(response.slots().get(0).totalCapacity()).isEqualTo(1);
    }

    @Test
    void returnsReceptionAvailabilityForActiveDentistWithoutPublicProfile() {
        LocalDate tuesday = LocalDate.parse("2026-10-20");
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(appointments.findScheduledBetween(any(), any())).thenReturn(List.of());
        when(requests.findActiveRequestsBetween(any(), any())).thenReturn(List.of());

        AppointmentAvailabilityResponse response = service.getAdministrativeAvailability(
                tuesday, dentist.getId(), ProfessionalServiceCode.GENERAL_DENTISTRY);

        assertThat(response.professionalId()).isEqualTo(dentist.getId());
        assertThat(response.date()).isEqualTo(tuesday);
        assertThat(response.slots()).hasSize(11);
        assertThat(response.slots()).allMatch(slot -> slot.status() == SlotStatus.AVAILABLE);
    }

    @Test
    void rejectsInactiveOrNonDentistOnReceptionAvailability() {
        dentist.setStatus(UserStatus.INACTIVE);
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getAdministrativeAvailability(
                        LocalDate.parse("2026-10-20"), dentist.getId(), ProfessionalServiceCode.GENERAL_DENTISTRY))
                .isInstanceOf(com.dentalcare.api.exception.BadRequestException.class)
                .hasMessageContaining("active dentist");
    }

    @Test
    void marksSundayAsEmptyOrClosed() {
        LocalDate sunday = LocalDate.parse("2026-10-25");

        AppointmentAvailabilityResponse response = service.getPublicAvailability(
                sunday, null, ProfessionalServiceCode.GENERAL_DENTISTRY);

        assertThat(response.slots()).isEmpty();
    }

    @Test
    void marksSlotAsBookedWhenDentistHasScheduledAppointment() {
        LocalDate tuesday = LocalDate.parse("2026-10-20");
        Instant slotTime = tuesday.atTime(LocalTime.of(8, 0))
                .atZone(AppointmentAvailabilityServiceImpl.CLINIC_ZONE).toInstant();

        Appointment bookedAppointment = new Appointment(
                UUID.randomUUID(), dentist, slotTime, AppointmentStatus.SCHEDULED,
                "Paciente Prueba", "55550000", NOW, NOW);

        when(profiles.findPubliclyVisibleByServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY))
                .thenReturn(List.of(profile));
        when(appointments.findScheduledBetween(any(), any())).thenReturn(List.of(bookedAppointment));
        when(requests.findActiveRequestsBetween(any(), any())).thenReturn(List.of());

        AppointmentAvailabilityResponse response = service.getPublicAvailability(
                tuesday, null, ProfessionalServiceCode.GENERAL_DENTISTRY);

        var firstSlot = response.slots().stream()
                .filter(s -> s.slotTime().equals(slotTime))
                .findFirst().orElseThrow();

        assertThat(firstSlot.status()).isEqualTo(SlotStatus.BOOKED);
        assertThat(firstSlot.availableCapacity()).isZero();
    }

    @Test
    void marksSlotAsRequestedWhenPendingDemandExistsButNotYetBooked() {
        LocalDate tuesday = LocalDate.parse("2026-10-20");
        Instant slotTime = tuesday.atTime(LocalTime.of(8, 45))
                .atZone(AppointmentAvailabilityServiceImpl.CLINIC_ZONE).toInstant();

        AppointmentRequest pendingReq = new AppointmentRequest(
                UUID.randomUUID(), null, dentist, slotTime, AppointmentRequestStatus.PENDING, NOW, NOW);

        when(profiles.findPubliclyVisibleByServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY))
                .thenReturn(List.of(profile));
        when(appointments.findScheduledBetween(any(), any())).thenReturn(List.of());
        when(requests.findActiveRequestsBetween(any(), any())).thenReturn(List.of(pendingReq));

        AppointmentAvailabilityResponse response = service.getPublicAvailability(
                tuesday, null, ProfessionalServiceCode.GENERAL_DENTISTRY);

        var slot = response.slots().stream()
                .filter(s -> s.slotTime().equals(slotTime))
                .findFirst().orElseThrow();

        assertThat(slot.status()).isEqualTo(SlotStatus.REQUESTED);
        assertThat(slot.availableCapacity()).isEqualTo(1);
    }
}
