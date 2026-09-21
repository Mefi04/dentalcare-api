package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.RefreshResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;

import java.time.Duration;
import java.util.UUID;

public interface AuthService {

    LoginResult login(LoginRequest request);

    RefreshResult refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    UserResponse getCurrentUser(UUID userId);

    record LoginResult(LoginResponse response, String refreshToken, Duration cookieMaxAge) {}

    record RefreshResult(RefreshResponse response, String refreshToken, Duration cookieMaxAge) {}
}
