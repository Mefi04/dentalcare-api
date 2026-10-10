package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.*;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralDentistryAvailabilityServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 12);
    private static final Instant SLOT = DATE.atTime(10, 0)
            .atZone(GeneralDentistryAvailabilityService.CLINIC_ZONE).toInstant();
    @Mock ProfessionalPublicProfileRepository profiles;
    @Mock ProfessionalWorkIntervalRepository hours;
    @Mock ProfessionalScheduleBlockRepository blocks;
    @Mock AppointmentRepository appointments;
    @Mock AppointmentRequestRepository requests;

    @Test
    void oneBusyGeneralDentistLeavesAggregateSlotAvailable() {
        var first = user("first");
        var second = user("second");
        setDentists(first, second);
        when(appointments.findByProfessional_IdInAndStatusAndScheduledAtLessThanAndEndsAtGreaterThan(
                anyList(), eq(AppointmentStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(new Appointment(UUID.randomUUID(), null, first, SLOT,
                        AppointmentStatus.SCHEDULED, NOW, NOW)));
        var result = service().forDate(DATE);
        var slot = result.slots().stream().filter(s -> s.startsAt().equals(SLOT)).findFirst().orElseThrow();
        assertThat(slot.status()).isEqualTo("AVAILABLE");
        assertThat(slot.availableProfessionals()).isEqualTo(1);
    }

    @Test
    void pendingRequestsDoNotReserveCapacity() {
        var first = user("third");
        var second = user("fourth");
        setDentists(first, second);
        var pending = new AppointmentRequest(UUID.randomUUID(), null, null, SLOT,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Visitor", null,
                "+50255550101", null, null, UUID.randomUUID(), "hash");
        when(requests.findPendingPublicInWindow(any(), any())).thenReturn(List.of(pending, pending));
        var slot = service().forDate(DATE).slots().stream()
                .filter(s -> s.startsAt().equals(SLOT)).findFirst().orElseThrow();
        assertThat(slot.status()).isEqualTo("REQUESTED");
        assertThat(slot.availableProfessionals()).isEqualTo(2);
        assertThat(slot.pendingRequests()).isEqualTo(2);
    }

    private void setDentists(User first, User second) {
        when(profiles.findActiveGeneralDentists()).thenReturn(List.of(profile(first), profile(second)));
        when(hours.findByProfessionalIdInAndDayOfWeekAndActiveTrue(anyList(), eq(DATE.getDayOfWeek().getValue())))
                .thenReturn(List.of(new ProfessionalWorkInterval(UUID.randomUUID(), first.getId(),
                        DATE.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(17, 0)),
                        new ProfessionalWorkInterval(UUID.randomUUID(), second.getId(),
                        DATE.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(17, 0))));
    }

    private ProfessionalPublicProfile profile(User user) {
        var profile = new ProfessionalPublicProfile(UUID.randomUUID(), user, "REG",
                "General", "Dentist", 5, null, null, true,
                user.getId(), user.getId(), NOW, NOW);
        profile.setServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY);
        return profile;
    }

    private User user(String name) {
        return new User(UUID.randomUUID(), name, "Dr " + name, name + "@test.local",
                "1234567890123", "hash", UserStatus.ACTIVE, NOW, NOW);
    }

    private GeneralDentistryAvailabilityService service() {
        return new GeneralDentistryAvailabilityService(profiles, hours, blocks, appointments,
                requests, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
