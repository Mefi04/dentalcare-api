package com.dentalcare.api.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestSecurityController {
    @GetMapping("/test/security/admin")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    String admin() {
        return "ok";
    }

    @GetMapping("/test/security/patient-read")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    String patientRead() {
        return "ok";
    }
}
