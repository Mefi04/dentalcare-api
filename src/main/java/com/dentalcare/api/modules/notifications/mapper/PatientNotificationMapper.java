package com.dentalcare.api.modules.notifications.mapper;

import com.dentalcare.api.modules.notifications.dto.response.NotificationPreferencesResponse;
import com.dentalcare.api.modules.notifications.dto.response.PatientNotificationResponse;
import com.dentalcare.api.modules.notifications.model.PatientNotification;
import com.dentalcare.api.modules.notifications.model.PatientNotificationPreference;
import org.springframework.stereotype.Component;

@Component
public class PatientNotificationMapper {
    public PatientNotificationResponse toResponse(PatientNotification notification) {
        return new PatientNotificationResponse(notification.getId(), notification.getEventType(),
                notification.getEventType().category(), notification.getTitle(), notification.getMessage(),
                notification.getReadAt() != null, notification.getReadAt(), notification.getCreatedAt());
    }

    public NotificationPreferencesResponse toResponse(PatientNotificationPreference preference) {
        return new NotificationPreferencesResponse(preference.isAppointmentsEnabled(),
                preference.isPaymentsEnabled(), preference.isMedicationsEnabled(),
                preference.isClinicUpdatesEnabled(), preference.getUpdatedAt());
    }
}
