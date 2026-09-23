package com.dentalcare.api.modules.users.controller;

import com.dentalcare.api.modules.users.dto.response.RoleResponse;
import com.dentalcare.api.modules.users.service.StaffUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "Roles", description = "Staff role catalog")
public class RoleController {
    private final StaffUserService service;
    public RoleController(StaffUserService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List active staff roles")
    public ResponseEntity<List<RoleResponse>> findActiveRoles() {
        return ResponseEntity.ok(service.findActiveRoles());
    }
}
