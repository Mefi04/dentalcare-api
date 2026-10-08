package com.dentalcare.api.modules.notifications.service;

import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.notifications.dto.request.UpdateNotificationPreferencesRequest;
import com.dentalcare.api.modules.notifications.mapper.PatientNotificationMapper;
import com.dentalcare.api.modules.notifications.model.*;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationPreferenceRepository;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientNotificationServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    @Mock PatientRepository patients;
    @Mock PatientNotificationRepository notifications;
    @Mock PatientNotificationPreferenceRepository preferences;
    private PatientNotificationService service;
    private UUID userId;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new PatientNotificationServiceImpl(patients, notifications, preferences,
                new PatientNotificationMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
    }

    @Test
    void listsOnlyNotificationsOwnedByAuthenticatedPatient() {
        PatientNotification own = notification(patient, null);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(notifications.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(own)));

        var result = service.findOwn(userId, 0, 20);

        assertThat(result.getContent()).singleElement().satisfies(value -> {
            assertThat(value.id()).isEqualTo(own.getId());
            assertThat(value.read()).isFalse();
            assertThat(value.category()).isEqualTo(NotificationCategory.APPOINTMENT);
        });
        verify(notifications).findByPatient_Id(eq(patient.getId()), any());
    }

    @Test
    void marksOwnedNotificationReadAndForeignIdIsIndistinguishableFromMissing() {
        PatientNotification own = notification(patient, null);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(notifications.findByIdAndPatient_Id(own.getId(), patient.getId())).thenReturn(Optional.of(own));

        assertThat(service.markAsRead(userId, own.getId()).readAt()).isEqualTo(NOW);

        UUID foreignId = UUID.randomUUID();
        when(notifications.findByIdAndPatient_Id(foreignId, patient.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markAsRead(userId, foreignId))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Notification not found");
        verify(notifications, never()).findById(foreignId);
    }

    @Test
    void marksAllUnreadOwnedNotificationsAndUpdatesPreferences() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(notifications.markAllUnreadAsRead(patient.getId(), NOW)).thenReturn(3);
        when(preferences.findByPatient_Id(patient.getId())).thenReturn(Optional.empty());
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.markAllAsRead(userId)).isEqualTo(3);
        var updated = service.updatePreferences(userId,
                new UpdateNotificationPreferencesRequest(true, false, true, false));

        assertThat(updated.appointmentsEnabled()).isTrue();
        assertThat(updated.paymentsEnabled()).isFalse();
        assertThat(updated.medicationsEnabled()).isTrue();
        assertThat(updated.clinicUpdatesEnabled()).isFalse();
    }

    @Test
    void createsAndPersistsDefaultPreferencesOnFirstRead() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(preferences.findByPatient_Id(patient.getId())).thenReturn(Optional.empty());
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.findPreferences(userId);

        assertThat(result.appointmentsEnabled()).isTrue();
        assertThat(result.paymentsEnabled()).isTrue();
        assertThat(result.medicationsEnabled()).isTrue();
        assertThat(result.clinicUpdatesEnabled()).isTrue();
        verify(preferences).save(any(PatientNotificationPreference.class));
    }

    @Test
    void publisherPersistsSupportedEventOnlyWhenCategoryIsEnabled() {
        var publisher = new PatientNotificationPublisherImpl(patients, notifications, preferences,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(preferences.findByPatient_Id(patient.getId())).thenReturn(Optional.of(
                new PatientNotificationPreference(UUID.randomUUID(), patient,
                        true, false, true, true, NOW, NOW)));

        publisher.publish(patient.getId(), NotificationEventType.APPOINTMENT_RESCHEDULED,
                "Cita reprogramada", "Tu cita cambió de fecha.");
        publisher.publish(patient.getId(), NotificationEventType.PAYMENT_REGISTERED,
                "Pago registrado", "Se registró tu pago.");

        verify(notifications, times(1)).save(any(PatientNotification.class));
    }

    @Test
    void publisherRejectsInvalidContentAndMissingPatient() {
        var publisher = new PatientNotificationPublisherImpl(patients, notifications, preferences,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> publisher.publish(patient.getId(), NotificationEventType.CLINIC_INFORMATION_UPDATED,
                " ", "Mensaje"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification title is required");
        verifyNoInteractions(preferences, notifications);

        when(patients.findById(patient.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> publisher.publish(patient.getId(), NotificationEventType.CLINIC_INFORMATION_UPDATED,
                "Información de clínica", "La clínica actualizó su información."))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
        verifyNoInteractions(preferences, notifications);
    }

    private PatientNotification notification(Patient owner, Instant readAt) {
        return new PatientNotification(UUID.randomUUID(), owner, NotificationEventType.APPOINTMENT_SCHEDULED,
                "Cita programada", "Tu cita fue programada.", readAt, NOW.minusSeconds(60));
    }
}
