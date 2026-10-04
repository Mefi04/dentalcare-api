package com.dentalcare.api.modules.settings.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ClinicSettingsResponse(short id, String tradeName, String nit, String phone, String email,
        String address, String city, String businessHours, String receiptPrefix, UUID updatedBy,
        Instant createdAt, Instant updatedAt) {}
