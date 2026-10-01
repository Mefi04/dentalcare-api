package com.dentalcare.api.modules.sterilization.controller;
import com.dentalcare.api.modules.sterilization.dto.request.*;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationProtocolResponse;
import com.dentalcare.api.modules.sterilization.service.SterilizationProtocolService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/sterilization/protocols")
public class SterilizationProtocolController {
 private final SterilizationProtocolService service;
 public SterilizationProtocolController(SterilizationProtocolService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('STERILIZATION_READ')")
 public ResponseEntity<Page<SterilizationProtocolResponse>> findAll(@RequestParam(required=false)String search,@RequestParam(required=false)Boolean active,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok(service.findAll(search,active,page,size));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('STERILIZATION_READ')") public ResponseEntity<SterilizationProtocolResponse> findById(@PathVariable UUID id){return ResponseEntity.ok(service.findById(id));}
 @PostMapping @PreAuthorize("hasAuthority('STERILIZATION_WRITE')") public ResponseEntity<SterilizationProtocolResponse> create(@Valid @RequestBody CreateSterilizationProtocolRequest request){var response=service.create(request);var uri=ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();return ResponseEntity.created(uri).body(response);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('STERILIZATION_WRITE')") public ResponseEntity<SterilizationProtocolResponse> update(@PathVariable UUID id,@Valid @RequestBody UpdateSterilizationProtocolRequest request){return ResponseEntity.ok(service.update(id,request));}
 @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('STERILIZATION_WRITE')") public ResponseEntity<SterilizationProtocolResponse> updateStatus(@PathVariable UUID id,@Valid @RequestBody UpdateSterilizationProtocolStatusRequest request){return ResponseEntity.ok(service.updateStatus(id,request.active()));}
}
