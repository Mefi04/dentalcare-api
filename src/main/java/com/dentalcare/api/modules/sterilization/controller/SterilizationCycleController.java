package com.dentalcare.api.modules.sterilization.controller;
import com.dentalcare.api.modules.sterilization.dto.request.*;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationCycleResponse;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycleStatus;
import com.dentalcare.api.modules.sterilization.service.SterilizationCycleService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.time.Instant;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/sterilization/cycles")
public class SterilizationCycleController {
 private final SterilizationCycleService service;
 public SterilizationCycleController(SterilizationCycleService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('STERILIZATION_READ')") public ResponseEntity<Page<SterilizationCycleResponse>> findAll(@RequestParam(required=false)UUID protocolId,@RequestParam(required=false)SterilizationCycleStatus status,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok(service.findAll(protocolId,status,from,to,page,size));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('STERILIZATION_READ')") public ResponseEntity<SterilizationCycleResponse> findById(@PathVariable UUID id){return ResponseEntity.ok(service.findById(id));}
 @PostMapping @PreAuthorize("hasAuthority('STERILIZATION_WRITE')") public ResponseEntity<SterilizationCycleResponse> create(@Valid @RequestBody CreateSterilizationCycleRequest request,@AuthenticationPrincipal AuthenticatedUser principal){var response=service.create(request,principal.userId());var uri=ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();return ResponseEntity.created(uri).body(response);}
 @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('STERILIZATION_WRITE')") public ResponseEntity<SterilizationCycleResponse> updateStatus(@PathVariable UUID id,@Valid @RequestBody UpdateSterilizationCycleStatusRequest request){return ResponseEntity.ok(service.updateStatus(id,request.status()));}
}
