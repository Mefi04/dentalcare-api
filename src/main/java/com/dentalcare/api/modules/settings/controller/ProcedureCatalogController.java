package com.dentalcare.api.modules.settings.controller;

import com.dentalcare.api.modules.settings.dto.request.*;
import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import com.dentalcare.api.modules.settings.service.ProcedureCatalogService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/catalog")
@Tag(name="Settings",description="Operational procedure catalog")
public class ProcedureCatalogController {
    private final ProcedureCatalogService service;
    public ProcedureCatalogController(ProcedureCatalogService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary="List and search procedure catalog items")
    public ResponseEntity<Page<ProcedureCatalogItemResponse>> findAll(@RequestParam(required=false)String search,
            @RequestParam(required=false)String category,@RequestParam(required=false)ProcedureCatalogItemStatus status,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return ResponseEntity.ok(service.findAll(search,category,status,page,size));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('SETTINGS_READ')")
    public ResponseEntity<ProcedureCatalogItemResponse> findById(@PathVariable UUID id){return ResponseEntity.ok(service.findById(id));}
    @PostMapping @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    public ResponseEntity<ProcedureCatalogItemResponse> create(@Valid @RequestBody CreateProcedureCatalogItemRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal){
        var response=service.create(request,principal.userId());
        URI location=ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    public ResponseEntity<ProcedureCatalogItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateProcedureCatalogItemRequest request,@AuthenticationPrincipal AuthenticatedUser principal){
        return ResponseEntity.ok(service.update(id,request,principal.userId()));
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    public ResponseEntity<ProcedureCatalogItemResponse> updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateProcedureCatalogItemStatusRequest request,@AuthenticationPrincipal AuthenticatedUser principal){
        return ResponseEntity.ok(service.updateStatus(id,request.status(),principal.userId()));
    }
}
