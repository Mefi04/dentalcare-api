package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.modules.settings.dto.request.UpdateClinicSettingsRequest;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import java.util.UUID;

public interface ClinicSettingsService {
    ClinicSettingsResponse get();
    ClinicSettingsResponse update(UpdateClinicSettingsRequest request, UUID actorId);
}
