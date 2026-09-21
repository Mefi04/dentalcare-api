package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    UserResponse getCurrentUser(UUID userId);
}
