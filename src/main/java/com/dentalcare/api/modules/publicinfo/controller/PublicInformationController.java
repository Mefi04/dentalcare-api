package com.dentalcare.api.modules.publicinfo.controller;

import com.dentalcare.api.modules.publicinfo.dto.response.PublicClinicResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicServiceResponse;
import com.dentalcare.api.modules.publicinfo.dto.response.PublicProfessionalResponse;
import com.dentalcare.api.modules.publicinfo.service.PublicInformationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "Public clinic information")
public class PublicInformationController {
    private final PublicInformationService service;

    public PublicInformationController(PublicInformationService service) {
        this.service = service;
    }

    @GetMapping("/clinic")
    @Operation(summary = "Get public clinic information")
    public ResponseEntity<PublicClinicResponse> getClinic() {
        return ResponseEntity.ok(service.getClinic());
    }

    @GetMapping("/services")
    @Operation(summary = "List active public services")
    public ResponseEntity<List<PublicServiceResponse>> getServices() {
        return ResponseEntity.ok(service.getServices());
    }

    @GetMapping("/professionals")
    @Operation(summary = "List publicly visible professionals")
    public ResponseEntity<List<PublicProfessionalResponse>> getProfessionals() {
        return ResponseEntity.ok(service.getProfessionals());
    }

    @GetMapping("/professionals/{id}")
    @Operation(summary = "Get a publicly visible professional")
    public ResponseEntity<PublicProfessionalResponse> getProfessional(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getProfessional(id));
    }
}
