package com.dentalcare.api.modules.notifications.dto.response;

import com.dentalcare.api.modules.notifications.model.NotificationCategory;
import com.dentalcare.api.modules.notifications.model.NotificationEventType;

import java.time.Instant;
import java.util.UUID;

public record PatientNotificationResponse(UUID id, NotificationEventType eventType, NotificationCategory category,
                                          String title, String message, boolean read, Instant readAt,
                                          Instant createdAt) {
}
