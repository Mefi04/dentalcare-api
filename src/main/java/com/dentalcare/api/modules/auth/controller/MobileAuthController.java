package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.request.MobileLogoutRequest;
import com.dentalcare.api.modules.auth.dto.request.MobileRefreshRequest;
import com.dentalcare.api.modules.auth.dto.response.MobileLoginResponse;
import com.dentalcare.api.modules.auth.dto.response.MobileRefreshResponse;
import com.dentalcare.api.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile Auth", description = "Mobile authentication and session renewal endpoints")
@RestController
@RequestMapping("/api/v1/auth/mobile")
public class MobileAuthController {

    private final AuthService authService;

    public MobileAuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Authenticate mobile client with CUI/DPI and password")
    @PostMapping("/login")
    public ResponseEntity<MobileLoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        MobileLoginResponse response = new MobileLoginResponse(
                result.response().accessToken(),
                result.refreshToken(),
                result.response().tokenType(),
                result.response().expiresIn(),
                result.response().user()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Renew mobile session with token rotation")
    @PostMapping("/refresh")
    public ResponseEntity<MobileRefreshResponse> refresh(@Valid @RequestBody MobileRefreshRequest request) {
        AuthService.RefreshResult result = authService.refresh(request.refreshToken());
        MobileRefreshResponse response = new MobileRefreshResponse(
                result.response().accessToken(),
                result.refreshToken(),
                result.response().tokenType(),
                result.response().expiresIn()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout mobile client and invalidate refresh session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) MobileLogoutRequest request) {
        String token = request != null ? request.refreshToken() : null;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
