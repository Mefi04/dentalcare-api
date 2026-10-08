package com.dentalcare.api.modules.notifications.controller;

import com.dentalcare.api.modules.notifications.dto.request.UpdateNotificationPreferencesRequest;
import com.dentalcare.api.modules.notifications.dto.response.MarkAllNotificationsReadResponse;
import com.dentalcare.api.modules.notifications.dto.response.NotificationPreferencesResponse;
import com.dentalcare.api.modules.notifications.dto.response.PatientNotificationResponse;
import com.dentalcare.api.modules.notifications.service.PatientNotificationService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/notifications")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient notifications", description = "In-app notifications owned by the authenticated patient")
public class PatientNotificationController {
    private final PatientNotificationService service;

    public PatientNotificationController(PatientNotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the authenticated patient's in-app notifications")
    public ResponseEntity<Page<PatientNotificationResponse>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findOwn(principal.userId(), page, size));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark one owned notification as read")
    public ResponseEntity<PatientNotificationResponse> markAsRead(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID notificationId) {
        return ResponseEntity.ok(service.markAsRead(principal.userId(), notificationId));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark every unread owned notification as read")
    public ResponseEntity<MarkAllNotificationsReadResponse> markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(new MarkAllNotificationsReadResponse(service.markAllAsRead(principal.userId())));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get the authenticated patient's notification preferences")
    public ResponseEntity<NotificationPreferencesResponse> findPreferences(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.findPreferences(principal.userId()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Replace the authenticated patient's notification preferences")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(service.updatePreferences(principal.userId(), request));
    }
}
