package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentContactAttemptRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentContactAttempt;
import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentContactAttemptRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentContactServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    @Mock AppointmentContactAttemptRepository attempts;
    @Mock AppointmentRequestRepository requests;
    @Mock UserRepository users;
    @Mock AuditService audit;

    @Test
    void recordingCallPreservesPendingStatusAndAuditsActor() {
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var request = publicRequest(requestId);
        var actor = new User();
        // The service derives actor identity from authentication, never from the request body.
        when(requests.findDetailedByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service();

        var response = service.record(actorId, requestId,
                new CreateAppointmentContactAttemptRequest(AppointmentContactResult.NO_ANSWER,
                        " Call again ", NOW.plusSeconds(3600)));

        assertThat(response.result()).isEqualTo(AppointmentContactResult.NO_ANSWER);
        assertThat(response.observation()).isEqualTo("Call again");
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        var saved = ArgumentCaptor.forClass(AppointmentContactAttempt.class);
        verify(attempts).save(saved.capture());
        assertThat(saved.getValue().getActor()).isSameAs(actor);
        verify(audit).success(eq("APPOINTMENT_CONTACT_ATTEMPT_RECORDED"), eq("APPOINTMENTS"),
                eq("AppointmentContactAttempt"), eq(response.id()), eq(actorId));
    }

    @Test
    void nonPublicRequestCannotReceiveCallHistory() {
        UUID requestId = UUID.randomUUID();
        when(requests.findDetailedByIdForUpdate(requestId)).thenReturn(Optional.of(
                new AppointmentRequest(requestId, null, null, NOW.plusSeconds(3600),
                        AppointmentRequestStatus.PENDING, NOW, NOW)));
        assertThatThrownBy(() -> service().record(UUID.randomUUID(), requestId,
                new CreateAppointmentContactAttemptRequest(AppointmentContactResult.CONTACTED, null, null)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(attempts, audit);
    }

    private AppointmentContactService service() {
        return new AppointmentContactService(attempts, requests, users, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AppointmentRequest publicRequest(UUID id) {
        return new AppointmentRequest(id, null, null, NOW.plusSeconds(3600),
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Test Caller", null,
                "+50255550101", null, null, UUID.randomUUID(), "hash");
    }
}
