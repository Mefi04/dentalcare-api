package com.dentalcare.api.modules.notifications.service;

import com.dentalcare.api.modules.notifications.dto.request.UpdateNotificationPreferencesRequest;
import com.dentalcare.api.modules.notifications.dto.response.NotificationPreferencesResponse;
import com.dentalcare.api.modules.notifications.dto.response.PatientNotificationResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PatientNotificationService {
    Page<PatientNotificationResponse> findOwn(UUID authenticatedUserId, int page, int size);
    PatientNotificationResponse markAsRead(UUID authenticatedUserId, UUID notificationId);
    int markAllAsRead(UUID authenticatedUserId);
    NotificationPreferencesResponse findPreferences(UUID authenticatedUserId);
    NotificationPreferencesResponse updatePreferences(UUID authenticatedUserId,
                                                      UpdateNotificationPreferencesRequest request);
}
