package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.dto.request.ActivateAccountRequest;
import com.dentalcare.api.modules.auth.dto.request.ChangePasswordRequest;
import com.dentalcare.api.modules.auth.dto.request.ChangeInitialPasswordRequest;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.ActivateAccountResponse;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.dto.response.PasswordChangeRequiredResponse;

import java.time.Duration;
import java.util.UUID;

public interface AuthService {

    ActivateAccountResponse activate(ActivateAccountRequest request);

    LoginResult login(LoginRequest request);

    WebLoginResult loginWeb(LoginRequest request);

    LoginResult completeInitialPasswordChange(String passwordChangeToken, ChangeInitialPasswordRequest request);

    RefreshResult refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void changePassword(UUID userId, ChangePasswordRequest request);

    UserResponse getCurrentUser(UUID userId);

    record LoginResult(LoginResponse response, String refreshToken, Duration cookieMaxAge) {}

    record WebLoginResult(LoginResult login, PasswordChangeRequiredResponse passwordChangeRequired) {}

    record RefreshResult(RefreshResponse response, String refreshToken, Duration cookieMaxAge) {}
}
