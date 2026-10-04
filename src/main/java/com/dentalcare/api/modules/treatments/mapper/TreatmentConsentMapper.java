package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentConsentResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentConsent;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

@Component
public class TreatmentConsentMapper {
    public TreatmentConsentResponse toResponse(TreatmentConsent consent) {
        return new TreatmentConsentResponse(
                consent.getId(), consent.getTreatmentPlan().getId(), consent.getPatient().getId(),
                consent.getTreatmentBudget() == null ? null : consent.getTreatmentBudget().getId(),
                consent.getDocumentVersion(), consent.getConsentText(), consent.getStatus(),
                actor(consent.getPreparedBy()), actor(consent.getAcceptedBy()), actor(consent.getRevokedBy()),
                consent.getCreatedAt(), consent.getUpdatedAt(), consent.getAcceptedAt(), consent.getRevokedAt());
    }

    private TreatmentPlanProfessionalResponse actor(User user) {
        return user == null ? null : new TreatmentPlanProfessionalResponse(user.getId(), user.getFullName());
    }
}
