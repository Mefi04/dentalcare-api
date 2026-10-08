package com.dentalcare.api.modules.notifications.service;

import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.notifications.dto.request.UpdateNotificationPreferencesRequest;
import com.dentalcare.api.modules.notifications.dto.response.NotificationPreferencesResponse;
import com.dentalcare.api.modules.notifications.dto.response.PatientNotificationResponse;
import com.dentalcare.api.modules.notifications.mapper.PatientNotificationMapper;
import com.dentalcare.api.modules.notifications.model.PatientNotification;
import com.dentalcare.api.modules.notifications.model.PatientNotificationPreference;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationPreferenceRepository;
import com.dentalcare.api.modules.notifications.repository.PatientNotificationRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PatientNotificationServiceImpl implements PatientNotificationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PatientRepository patientRepository;
    private final PatientNotificationRepository notificationRepository;
    private final PatientNotificationPreferenceRepository preferenceRepository;
    private final PatientNotificationMapper mapper;
    private final Clock clock;

    public PatientNotificationServiceImpl(PatientRepository patientRepository,
                                          PatientNotificationRepository notificationRepository,
                                          PatientNotificationPreferenceRepository preferenceRepository,
                                          PatientNotificationMapper mapper, Clock clock) {
        this.patientRepository = patientRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientNotificationResponse> findOwn(UUID authenticatedUserId, int page, int size) {
        Patient patient = patient(authenticatedUserId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE), ORDER);
        return notificationRepository.findByPatient_Id(patient.getId(), pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public PatientNotificationResponse markAsRead(UUID authenticatedUserId, UUID notificationId) {
        Patient patient = patient(authenticatedUserId);
        PatientNotification notification = notificationRepository
                .findByIdAndPatient_Id(notificationId, patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now(clock));
        }
        return mapper.toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID authenticatedUserId) {
        Patient patient = patient(authenticatedUserId);
        return notificationRepository.markAllUnreadAsRead(patient.getId(), Instant.now(clock));
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse findPreferences(UUID authenticatedUserId) {
        Patient patient = patient(authenticatedUserId);
        return mapper.toResponse(preferenceRepository.findByPatient_Id(patient.getId())
                .orElseGet(() -> preferenceRepository.save(defaultPreferences(patient))));
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID authenticatedUserId,
                                                              UpdateNotificationPreferencesRequest request) {
        Patient patient = patient(authenticatedUserId);
        PatientNotificationPreference preference = preferenceRepository.findByPatient_Id(patient.getId())
                .orElseGet(() -> defaultPreferences(patient));
        preference.update(request.appointmentsEnabled(), request.paymentsEnabled(), request.medicationsEnabled(),
                request.clinicUpdatesEnabled(), Instant.now(clock));
        return mapper.toResponse(preferenceRepository.save(preference));
    }

    private Patient patient(UUID authenticatedUserId) {
        return patientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
    }

    private PatientNotificationPreference defaultPreferences(Patient patient) {
        Instant now = Instant.now(clock);
        return new PatientNotificationPreference(UUID.randomUUID(), patient, true, true, true, true, now, now);
    }
}
