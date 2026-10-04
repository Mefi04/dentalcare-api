package com.dentalcare.api.modules.settings.mapper;

import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.model.ClinicSettings;
import org.springframework.stereotype.Component;

@Component
public class ClinicSettingsMapper {
    public ClinicSettingsResponse toResponse(ClinicSettings value) {
        return new ClinicSettingsResponse(value.getId(), value.getTradeName(), value.getNit(), value.getPhone(),
                value.getEmail(), value.getAddress(), value.getCity(), value.getBusinessHours(),
                value.getReceiptPrefix(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
