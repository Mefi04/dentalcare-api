package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.*;
import com.dentalcare.api.modules.appointments.dto.response.*;
import com.dentalcare.api.modules.appointments.service.ProfessionalScheduleService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointment-professionals/{professionalId}/schedule")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ProfessionalScheduleController {
    private final ProfessionalScheduleService service;
    public ProfessionalScheduleController(ProfessionalScheduleService service) { this.service = service; }

    @GetMapping("/work-intervals")
    public List<ProfessionalWorkIntervalResponse> workIntervals(@PathVariable UUID professionalId) {
        return service.workIntervals(professionalId);
    }

    @GetMapping("/blocks")
    public List<ProfessionalScheduleBlockResponse> blocks(@PathVariable UUID professionalId) {
        return service.futureBlocks(professionalId);
    }

    @PostMapping("/work-intervals")
    public ResponseEntity<ProfessionalWorkIntervalResponse> addWork(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID professionalId,
            @Valid @RequestBody CreateProfessionalWorkIntervalRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addWork(actor.userId(), professionalId, input));
    }

    @DeleteMapping("/work-intervals/{intervalId}")
    public ResponseEntity<Void> removeWork(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID professionalId, @PathVariable UUID intervalId) {
        service.removeWork(actor.userId(), professionalId, intervalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/blocks")
    public ResponseEntity<ProfessionalScheduleBlockResponse> addBlock(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID professionalId,
            @Valid @RequestBody CreateProfessionalScheduleBlockRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addBlock(actor.userId(), professionalId, input));
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> removeBlock(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID professionalId, @PathVariable UUID blockId) {
        service.removeBlock(actor.userId(), professionalId, blockId);
        return ResponseEntity.noContent().build();
    }
}
