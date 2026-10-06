package com.dentalcare.api.modules.publicinfo.service;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicProfessionalResponse;
import java.util.List;
import java.util.UUID;

public interface PublicInformationService {
    PublicClinicResponse getClinic();
    List<PublicServiceResponse> getServices();
    List<PublicProfessionalResponse> getProfessionals();
    PublicProfessionalResponse getProfessional(UUID profileId);
}
