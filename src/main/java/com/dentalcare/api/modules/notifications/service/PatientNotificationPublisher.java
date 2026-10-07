package com.dentalcare.api.modules.notifications.service;

import com.dentalcare.api.modules.notifications.model.NotificationEventType;

import java.util.UUID;

/** Internal application contract for modules that own real domain events. */
public interface PatientNotificationPublisher {
    void publish(UUID patientId, NotificationEventType eventType, String title, String message);
}
