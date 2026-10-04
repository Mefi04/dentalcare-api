package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.modules.treatments.dto.request.PrepareTreatmentConsentRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentConsentResponse;

import java.util.List;
import java.util.UUID;

public interface TreatmentConsentService {
    TreatmentConsentResponse prepare(UUID planId, UUID actorId, PrepareTreatmentConsentRequest request);
    List<TreatmentConsentResponse> findByPlan(UUID planId);
    TreatmentConsentResponse findById(UUID consentId);
    TreatmentConsentResponse accept(UUID consentId, UUID actorId);
    TreatmentConsentResponse revoke(UUID consentId, UUID actorId);
}
