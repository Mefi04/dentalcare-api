package com.dentalcare.api.modules.publicinfo.dto.response;

import java.util.UUID;

public record PublicProfessionalResponse(UUID id, String fullName, String professionalRegistration, String specialty,
        String summary, Integer yearsExperience, String languages, String photoUrl) { }
