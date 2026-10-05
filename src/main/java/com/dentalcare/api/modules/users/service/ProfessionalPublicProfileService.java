package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.modules.users.dto.request.UpsertProfessionalPublicProfileRequest;
import com.dentalcare.api.modules.users.dto.response.ProfessionalPublicProfileResponse;
import java.util.List;
import java.util.UUID;

public interface ProfessionalPublicProfileService {
    ProfessionalPublicProfileResponse findByUserId(UUID userId);
    ProfessionalPublicProfileResponse upsert(UUID userId, UpsertProfessionalPublicProfileRequest request, UUID actorId);
    List<ProfessionalPublicProfileResponse> findPubliclyVisible();
    ProfessionalPublicProfileResponse findPubliclyVisibleById(UUID profileId);
}
