package com.dentalcare.api.modules.users.dto.response;

import java.util.UUID;

public record ProfessionalPublicProfileResponse(UUID id, UUID userId, String fullName, String professionalRegistration,
        String specialty, String summary, Integer yearsExperience, String languages, String photoUrl, boolean publicVisible) { }
