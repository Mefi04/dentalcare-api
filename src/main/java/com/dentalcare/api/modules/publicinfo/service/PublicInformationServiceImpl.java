package com.dentalcare.api.modules.publicinfo.service;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicProfessionalResponse;
import com.dentalcare.api.modules.publicinfo.mapper.PublicInformationMapper;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
import com.dentalcare.api.modules.settings.service.ProcedureCatalogService;
import com.dentalcare.api.modules.users.service.ProfessionalPublicProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PublicInformationServiceImpl implements PublicInformationService {
    private final ClinicSettingsService clinicSettingsService;
    private final ProcedureCatalogService procedureCatalogService;
    private final PublicInformationMapper mapper;
    private final ProfessionalPublicProfileService professionalProfiles;

    public PublicInformationServiceImpl(ClinicSettingsService clinicSettingsService,
                                        ProcedureCatalogService procedureCatalogService,
                                        PublicInformationMapper mapper, ProfessionalPublicProfileService professionalProfiles) {
        this.clinicSettingsService = clinicSettingsService;
        this.procedureCatalogService = procedureCatalogService;
        this.mapper = mapper;
        this.professionalProfiles = professionalProfiles;
    }

    @Override @Transactional(readOnly = true)
    public PublicClinicResponse getClinic() {
        return mapper.toClinicResponse(clinicSettingsService.get());
    }

    @Override @Transactional(readOnly = true)
    public List<PublicServiceResponse> getServices() {
        return procedureCatalogService.findActive().stream().map(mapper::toServiceResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PublicProfessionalResponse> getProfessionals() {
        return professionalProfiles.findPubliclyVisible().stream().map(mapper::toProfessionalResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public PublicProfessionalResponse getProfessional(java.util.UUID profileId) {
        return mapper.toProfessionalResponse(professionalProfiles.findPubliclyVisibleById(profileId));
    }
}
