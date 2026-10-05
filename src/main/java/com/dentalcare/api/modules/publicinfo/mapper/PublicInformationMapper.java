package com.dentalcare.api.modules.publicinfo.mapper;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import org.springframework.stereotype.Component;

@Component
public class PublicInformationMapper {
    public PublicClinicResponse toClinicResponse(ClinicSettingsResponse settings) {
        return new PublicClinicResponse(settings.tradeName(), settings.phone(), settings.email(), settings.address(),
                settings.city(), settings.businessHours());
    }

    public PublicServiceResponse toServiceResponse(ProcedureCatalogItemResponse item) {
        return new PublicServiceResponse(item.code(), item.name(), item.category(), item.durationMinutes());
    }
}
