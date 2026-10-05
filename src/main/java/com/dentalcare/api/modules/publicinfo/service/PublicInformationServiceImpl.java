package com.dentalcare.api.modules.publicinfo.service;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.mapper.PublicInformationMapper;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
import com.dentalcare.api.modules.settings.service.ProcedureCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PublicInformationServiceImpl implements PublicInformationService {
    private final ClinicSettingsService clinicSettingsService;
    private final ProcedureCatalogService procedureCatalogService;
    private final PublicInformationMapper mapper;

    public PublicInformationServiceImpl(ClinicSettingsService clinicSettingsService,
                                        ProcedureCatalogService procedureCatalogService,
                                        PublicInformationMapper mapper) {
        this.clinicSettingsService = clinicSettingsService;
        this.procedureCatalogService = procedureCatalogService;
        this.mapper = mapper;
    }

    @Override @Transactional(readOnly = true)
    public PublicClinicResponse getClinic() {
        return mapper.toClinicResponse(clinicSettingsService.get());
    }

    @Override @Transactional(readOnly = true)
    public List<PublicServiceResponse> getServices() {
        return procedureCatalogService.findActive().stream().map(mapper::toServiceResponse).toList();
    }
}
