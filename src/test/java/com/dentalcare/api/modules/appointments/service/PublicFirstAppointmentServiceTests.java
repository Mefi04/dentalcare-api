package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.request.FirstAppointmentIntakeRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.patients.model.Gender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicFirstAppointmentServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-12T16:00:00Z");
    @Mock AppointmentRequestRepository requests;
    @Mock GeneralDentistryAvailabilityService availability;
    @Mock AuditService audit;

    @Test
    void tenIndependentRequestsForOneSlotStayPendingAndCreateNoAppointment() {
        when(requests.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(availability.forDate(any())).thenReturn(new PublicAppointmentAvailabilityResponse(
                LocalDate.of(2026, 10, 12), "America/Guatemala", 30,
                List.of(new PublicAppointmentSlotResponse(SLOT, "REQUESTED", 2, 9))));
        var service = service();
        for (int number = 0; number < 10; number++) {
            var result = service.submit(adult("Visitor " + number), UUID.randomUUID());
            assertThat(result.confirmed()).isFalse();
            assertThat(result.message()).contains("todavía no está confirmada");
        }
        var captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests, times(10)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
            assertThat(saved.getAppointment()).isNull();
            assertThat(saved.getPrivacyAcceptedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void consentIsRequiredBeforePersistence() {
        var input = adult("Visitor");
        var withoutConsent = new FirstAppointmentIntakeRequest(input.fullName(), input.cui(),
                input.birthDate(), input.gender(), input.alternativeId(), input.guardianName(),
                input.guardianRelationship(), input.guardianPhone(), input.phone(), input.email(),
                input.department(), input.municipality(), input.address(), input.emergencyName(),
                input.emergencyPhone(), input.nit(), input.billingName(), input.billingAddress(),
                input.requestedAt(), input.logisticsNote(), false, input.privacyNoticeVersion());
        assertThatThrownBy(() -> service().submit(withoutConsent, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(requests, availability);
    }

    @Test
    void minorNeedsGuardianButNoInventedDpi() {
        var input = new FirstAppointmentIntakeRequest("Minor", null, LocalDate.of(2015, 1, 1),
                Gender.FEMALE, null, null, null, null, "+50255550101", null,
                "Guatemala", "Guatemala", "Zona 1", "Emergency", "+50255550102",
                null, null, null, SLOT, null, true, "2026-10");
        assertThatThrownBy(() -> service().submit(input, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("guardian");
    }

    private FirstAppointmentIntakeRequest adult(String name) {
        return new FirstAppointmentIntakeRequest(name, "1234567890123",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, null, null, null, null,
                "+50255550101", null, "Guatemala", "Guatemala", "Zona 1",
                "Emergency", "+50255550102", null, null, null, SLOT,
                null, true, "2026-10");
    }

    private PublicFirstAppointmentService service() {
        return new PublicFirstAppointmentService(requests, availability, audit,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
