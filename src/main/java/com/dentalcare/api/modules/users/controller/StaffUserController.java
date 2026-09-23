package com.dentalcare.api.modules.users.controller;

import com.dentalcare.api.modules.users.dto.request.CreateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.dentalcare.api.modules.users.dto.response.CreateStaffUserResponse;
import com.dentalcare.api.modules.users.dto.response.StaffUserResponse;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.service.StaffUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "Staff users", description = "Administrative staff account management")
public class StaffUserController {
    private final StaffUserService service;
    public StaffUserController(StaffUserService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create a staff account")
    public ResponseEntity<CreateStaffUserResponse> create(@Valid @RequestBody CreateStaffUserRequest request) {
        CreateStaffUserResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.user().id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "List and filter staff accounts")
    public ResponseEntity<Page<StaffUserResponse>> search(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status, @RequestParam(required = false) String role) {
        return ResponseEntity.ok(service.search(page, size, search, status, role));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a staff account")
    public ResponseEntity<StaffUserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a staff account")
    public ResponseEntity<StaffUserResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateStaffUserRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a staff account status")
    public ResponseEntity<StaffUserResponse> updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request));
    }
}
