package com.dentalcare.api.modules.users.dto.response;

import java.util.UUID;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;

public record ProfessionalPublicProfileResponse(UUID id, UUID userId, String fullName, String professionalRegistration,
        String specialty, String summary, Integer yearsExperience, String languages, String photoUrl,
        boolean publicVisible, ProfessionalServiceCode serviceCode) {
    public ProfessionalPublicProfileResponse(UUID id, UUID userId, String fullName,
            String professionalRegistration, String specialty, String summary, Integer yearsExperience,
            String languages, String photoUrl, boolean publicVisible) {
        this(id, userId, fullName, professionalRegistration, specialty, summary,
                yearsExperience, languages, photoUrl, publicVisible, null);
    }
}
