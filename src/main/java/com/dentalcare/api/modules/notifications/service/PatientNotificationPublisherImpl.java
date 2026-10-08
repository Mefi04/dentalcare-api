package com.dentalcare.api.modules.notifications.service;

import com.dentalcare.api.modules.notifications.model.*;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationPreferenceRepository;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationRepository;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PatientNotificationPublisherImpl implements PatientNotificationPublisher {
    private static final int PATIENT_BATCH_SIZE = 100;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private final PatientRepository patientRepository;
    private final PatientNotificationRepository notificationRepository;
    private final PatientNotificationPreferenceRepository preferenceRepository;
    private final Clock clock;

    public PatientNotificationPublisherImpl(PatientRepository patientRepository,
                                            PatientNotificationRepository notificationRepository,
                                            PatientNotificationPreferenceRepository preferenceRepository,
                                            Clock clock) {
        this.patientRepository = patientRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publish(UUID patientId, NotificationEventType eventType, String title, String message) {
        requireContent(title, "title", MAX_TITLE_LENGTH);
        requireContent(message, "message", MAX_MESSAGE_LENGTH);
        var patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        persistWhenEnabled(patient, eventType, title, message);
    }

    @Override
    @Transactional
    public void publishToAll(NotificationEventType eventType, String title, String message) {
        requireContent(title, "title", MAX_TITLE_LENGTH);
        requireContent(message, "message", MAX_MESSAGE_LENGTH);
        int page = 0;
        var patients = patientRepository.findAll(patientPage(page));
        while (true) {
            patients.stream()
                    .filter(patient -> patient.getUser() != null)
                    .forEach(patient -> persistWhenEnabled(patient, eventType, title, message));
            if (!patients.hasNext()) {
                return;
            }
            patients = patientRepository.findAll(patientPage(++page));
        }
    }

    private PageRequest patientPage(int page) {
        return PageRequest.of(page, PATIENT_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
    }

    private void persistWhenEnabled(com.dentalcare.api.modules.patients.model.Patient patient,
                                    NotificationEventType eventType, String title, String message) {
        if (enabled(patient.getId(), eventType.category())) {
            notificationRepository.save(new PatientNotification(UUID.randomUUID(), patient, eventType,
                    title.strip(), message.strip(), null, Instant.now(clock)));
        }
    }

    private void requireContent(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification " + field + " is required");
        }
        if (value.strip().length() > maxLength) {
            throw new IllegalArgumentException("Notification " + field + " exceeds " + maxLength + " characters");
        }
    }

    private boolean enabled(UUID patientId, NotificationCategory category) {
        return preferenceRepository.findByPatient_Id(patientId)
                .map(preference -> switch (category) {
                    case APPOINTMENT -> preference.isAppointmentsEnabled();
                    case PAYMENT -> preference.isPaymentsEnabled();
                    case MEDICATION -> preference.isMedicationsEnabled();
                    case CLINIC_UPDATE -> preference.isClinicUpdatesEnabled();
                })
                .orElse(true);
    }
}
