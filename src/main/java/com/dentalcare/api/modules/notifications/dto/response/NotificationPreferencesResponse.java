package com.dentalcare.api.modules.notifications.dto.response;

import java.time.Instant;

public record NotificationPreferencesResponse(boolean appointmentsEnabled, boolean paymentsEnabled,
                                               boolean medicationsEnabled, boolean clinicUpdatesEnabled,
                                               Instant updatedAt) {
}
