package com.dentalcare.api.modules.auth.controller;

import com.dentalcare.api.modules.auth.dto.request.ActivateAccountRequest;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.request.ChangeInitialPasswordRequest;
import com.dentalcare.api.modules.auth.dto.response.ActivateAccountResponse;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.service.AuthService;
import com.dentalcare.api.security.cookie.AuthCookieManager;
import com.dentalcare.api.security.service.AuthenticatedUser;
import com.dentalcare.api.security.ratelimit.RateLimitPolicy;
import com.dentalcare.api.security.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieManager authCookieManager;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, AuthCookieManager authCookieManager, RateLimitService rateLimitService) {
        this.authService = authService;
        this.authCookieManager = authCookieManager;
        this.rateLimitService = rateLimitService;
    }

    @Operation(summary = "Legacy account activation retained for existing clients",
            description = "Compatible legacy flow. New clients should use login and change-initial.")
    @PostMapping("/activate")
    public ResponseEntity<ActivateAccountResponse> activate(@Valid @RequestBody ActivateAccountRequest request) {
        return ResponseEntity.ok(authService.activate(request));
    }

    @Operation(summary = "Authenticate with CUI/DPI and password",
            description = "Active accounts receive an authenticated session. A correct initial temporary password receives only a short-lived password-change token.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(oneOf = {
            LoginResponse.class, com.dentalcare.api.modules.auth.dto.response.PasswordChangeRequiredResponse.class
    })))
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(name = AuthCookieManager.TAB_SESSION_HEADER_NAME, required = false) String tabSessionId,
            HttpServletRequest httpRequest) {
        authCookieManager.cookieNameForTab(tabSessionId);
        rateLimitService.checkIdentity(RateLimitPolicy.LOGIN, request.cui());
        AuthService.WebLoginResult flow = authService.loginWeb(request);
        if (flow.passwordChangeRequired() != null) {
            return ResponseEntity.ok(flow.passwordChangeRequired());
        }
        AuthService.LoginResult result = flow.login();
        ResponseCookie cookie = authCookieManager.createRefreshCookie(
                result.refreshToken(),
                result.cookieMaxAge(),
                httpRequest.isSecure(),
                tabSessionId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @Operation(summary = "Change the initial password and create a tab-scoped authenticated session",
            description = "Requires Authorization: Bearer <passwordChangeToken> from login. Confirmation must exactly equal newPassword. Success returns the normal login response and sets the HttpOnly refresh cookie selected by X-Client-Session-Id.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @PostMapping("/password/change-initial")
    public ResponseEntity<LoginResponse> changeInitialPassword(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = AuthCookieManager.TAB_SESSION_HEADER_NAME, required = false) String tabSessionId,
            @Valid @RequestBody ChangeInitialPasswordRequest request,
            HttpServletRequest httpRequest) {
        authCookieManager.cookieNameForTab(tabSessionId);
        String token = bearerToken(authorization);
        AuthService.LoginResult result = authService.completeInitialPasswordChange(token, request);
        ResponseCookie cookie = authCookieManager.createRefreshCookie(
                result.refreshToken(), result.cookieMaxAge(), httpRequest.isSecure(), tabSessionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new com.dentalcare.api.exception.UnauthorizedException(
                    "Initial password-change token is invalid or expired");
        }
        return authorization.substring(7);
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @RequestHeader(name = AuthCookieManager.TAB_SESSION_HEADER_NAME, required = false) String tabSessionId,
            HttpServletRequest httpRequest) {
        String refreshToken = authCookieManager.findRefreshToken(httpRequest, tabSessionId);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.dentalcare.api.exception.UnauthorizedException("Authentication is required");
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        ResponseCookie cookie = authCookieManager.createRefreshCookie(
                result.refreshToken(),
                result.cookieMaxAge(),
                httpRequest.isSecure(),
                tabSessionId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @Operation(summary = "Logout and invalidate refresh session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = AuthCookieManager.TAB_SESSION_HEADER_NAME, required = false) String tabSessionId,
            HttpServletRequest httpRequest) {
        String refreshToken = authCookieManager.findRefreshToken(httpRequest, tabSessionId);
        authService.logout(refreshToken);
        ResponseCookie clearCookie = authCookieManager.createClearCookie(httpRequest.isSecure(), tabSessionId);
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
