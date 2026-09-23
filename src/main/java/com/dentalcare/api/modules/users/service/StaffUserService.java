package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.modules.users.dto.request.CreateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.dentalcare.api.modules.users.dto.response.CreateStaffUserResponse;
import com.dentalcare.api.modules.users.dto.response.RoleResponse;
import com.dentalcare.api.modules.users.dto.response.StaffUserResponse;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

public interface StaffUserService {
    CreateStaffUserResponse create(CreateStaffUserRequest request);
    Page<StaffUserResponse> search(int page, int size, String search, UserStatus status, String role);
    StaffUserResponse findById(UUID id);
    StaffUserResponse update(UUID id, UpdateStaffUserRequest request);
    StaffUserResponse updateStatus(UUID id, UpdateUserStatusRequest request);
    List<RoleResponse> findActiveRoles();
}
