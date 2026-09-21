package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.service.AuthService;
import com.dentalcare.api.security.cookie.AuthCookieManager;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieManager authCookieManager;

    public AuthController(AuthService authService, AuthCookieManager authCookieManager) {
        this.authService = authService;
        this.authCookieManager = authCookieManager;
    }

    @Operation(summary = "Authenticate with username or email")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request);
        ResponseCookie cookie = authCookieManager.createRefreshCookie(
                result.refreshToken(),
                result.cookieMaxAge(),
                httpRequest.isSecure()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = AuthCookieManager.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.dentalcare.api.exception.UnauthorizedException("Authentication is required");
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        ResponseCookie cookie = authCookieManager.createRefreshCookie(
                result.refreshToken(),
                result.cookieMaxAge(),
                httpRequest.isSecure()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @Operation(summary = "Logout and invalidate refresh session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieManager.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        authService.logout(refreshToken);
        ResponseCookie clearCookie = authCookieManager.createClearCookie(httpRequest.isSecure());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @Operation(summary = "Get the authenticated user")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.getCurrentUser(principal.userId()));
    }
}
