package com.dentalcare.api.modules.users.controller;

import com.dentalcare.api.modules.users.dto.request.UpsertProfessionalPublicProfileRequest;
import com.dentalcare.api.modules.users.dto.response.ProfessionalPublicProfileResponse;
import com.dentalcare.api.modules.users.service.ProfessionalPublicProfileService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/users/{userId}/public-profile") @PreAuthorize("hasRole('ADMINISTRATOR')")
public class ProfessionalPublicProfileController {
    private final ProfessionalPublicProfileService service;
    public ProfessionalPublicProfileController(ProfessionalPublicProfileService service){this.service=service;}
    @GetMapping @Operation(summary="Get a dentist's public profile for administration") public ResponseEntity<ProfessionalPublicProfileResponse> get(@PathVariable UUID userId){return ResponseEntity.ok(service.findByUserId(userId));}
    @PutMapping @Operation(summary="Create or replace a dentist's public profile") public ResponseEntity<ProfessionalPublicProfileResponse> upsert(@PathVariable UUID userId,@Valid @RequestBody UpsertProfessionalPublicProfileRequest request,@AuthenticationPrincipal AuthenticatedUser principal){return ResponseEntity.ok(service.upsert(userId,request,principal.userId()));}
}
