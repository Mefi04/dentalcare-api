package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.modules.auth.dto.request.ConfirmPasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.request.PasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.response.PasswordRecoveryResponse;
import com.dentalcare.api.modules.auth.service.PasswordRecoveryService;
import com.dentalcare.api.security.ratelimit.RateLimitPolicy;
import com.dentalcare.api.security.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Password Recovery", description = "Patient account password recovery")
@RestController
@RequestMapping("/api/v1/auth/password-recovery")
public class PasswordRecoveryController {

    private final PasswordRecoveryService passwordRecoveryService;
    private final RateLimitService rateLimitService;

    public PasswordRecoveryController(PasswordRecoveryService passwordRecoveryService, RateLimitService rateLimitService) {
        this.passwordRecoveryService = passwordRecoveryService;
        this.rateLimitService = rateLimitService;
    }

    @Operation(summary = "Request a one-time patient password recovery code")
    @PostMapping("/request")
    public ResponseEntity<PasswordRecoveryResponse> request(
            @Valid @RequestBody PasswordRecoveryRequest request) {
        rateLimitService.checkIdentity(RateLimitPolicy.PASSWORD_RECOVERY, request.cui());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(passwordRecoveryService.requestRecovery(request));
    }

    @Operation(summary = "Confirm a patient password recovery code")
    @PostMapping("/confirm")
    public ResponseEntity<PasswordRecoveryResponse> confirm(
            @Valid @RequestBody ConfirmPasswordRecoveryRequest request) {
        return ResponseEntity.ok(passwordRecoveryService.confirmRecovery(request));
    }
}
