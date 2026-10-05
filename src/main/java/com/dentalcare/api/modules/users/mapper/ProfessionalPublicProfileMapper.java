package com.dentalcare.api.modules.users.mapper;

import com.dentalcare.api.modules.users.dto.response.ProfessionalPublicProfileResponse;
import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalPublicProfileMapper {
    public ProfessionalPublicProfileResponse toResponse(ProfessionalPublicProfile profile) {
        return new ProfessionalPublicProfileResponse(profile.getId(), profile.getUser().getId(), profile.getUser().getFullName(),
                profile.getProfessionalRegistration(), profile.getSpecialty(), profile.getSummary(), profile.getYearsExperience(),
                profile.getLanguages(), profile.getPhotoUrl(), profile.isPublicVisible());
    }
}
