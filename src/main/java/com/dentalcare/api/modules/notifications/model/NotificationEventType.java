package com.dentalcare.api.modules.notifications.model;

public enum NotificationEventType {
    APPOINTMENT_SCHEDULED(NotificationCategory.APPOINTMENT),
    APPOINTMENT_RESCHEDULED(NotificationCategory.APPOINTMENT),
    APPOINTMENT_CANCELLED(NotificationCategory.APPOINTMENT),
    PAYMENT_REGISTERED(NotificationCategory.PAYMENT),
    PRESCRIPTION_ISSUED(NotificationCategory.MEDICATION),
    CLINIC_INFORMATION_UPDATED(NotificationCategory.CLINIC_UPDATE);

    private final NotificationCategory category;

    NotificationEventType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }
}
