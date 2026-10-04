package com.dentalcare.api.modules.settings.controller;

import com.dentalcare.api.modules.settings.dto.request.UpdateClinicSettingsRequest;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/clinic")
@Tag(name="Settings",description="Clinic configuration")
public class ClinicSettingsController {
    private final ClinicSettingsService service;
    public ClinicSettingsController(ClinicSettingsService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary="Get the singleton clinic configuration")
    public ResponseEntity<ClinicSettingsResponse> get(){return ResponseEntity.ok(service.get());}

    @PutMapping @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    @Operation(summary="Update the singleton clinic configuration")
    public ResponseEntity<ClinicSettingsResponse> update(@Valid @RequestBody UpdateClinicSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal){
        return ResponseEntity.ok(service.update(request,principal.userId()));
    }
}
