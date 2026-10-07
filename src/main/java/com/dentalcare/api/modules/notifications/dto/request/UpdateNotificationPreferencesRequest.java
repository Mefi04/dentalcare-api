package com.dentalcare.api.modules.notifications.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferencesRequest(
        @NotNull Boolean appointmentsEnabled,
        @NotNull Boolean paymentsEnabled,
        @NotNull Boolean medicationsEnabled,
        @NotNull Boolean clinicUpdatesEnabled) {
}
