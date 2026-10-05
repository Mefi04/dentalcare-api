package com.dentalcare.api.modules.publicinfo.service;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import java.util.List;

public interface PublicInformationService {
    PublicClinicResponse getClinic();
    List<PublicServiceResponse> getServices();
}
