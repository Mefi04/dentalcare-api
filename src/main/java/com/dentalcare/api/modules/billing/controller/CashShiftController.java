package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.service.CashShiftService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/cash-shifts")
@Tag(name = "Cash shifts", description = "Cash shift opening, closing and manual movements")
public class CashShiftController {

    private final CashShiftService cashShiftService;

    public CashShiftController(CashShiftService cashShiftService) {
        this.cashShiftService = cashShiftService;
    }

    @Operation(summary = "Get the authenticated user's open cash shift",
            description = "Expected amount is calculated by the backend and is not taken from the client.")
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('BILLING_CASH_MANAGE')")
    public ResponseEntity<CashShiftResponse> getCurrent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(cashShiftService.getCurrent(principal.userId()));
    }

    @Operation(summary = "Open a cash shift for the authenticated user")
    @PostMapping
    @PreAuthorize("hasAuthority('BILLING_CASH_MANAGE')")
    public ResponseEntity<CashShiftResponse> open(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OpenCashShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashShiftService.open(principal.userId(), request));
    }

    @Operation(summary = "Close the authenticated user's cash shift",
            description = "Expected amount and difference are calculated by the backend.")
    @PostMapping("/{shiftId}/close")
    @PreAuthorize("hasAuthority('BILLING_CASH_MANAGE')")
    public ResponseEntity<CashShiftResponse> close(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID shiftId,
            @Valid @RequestBody CloseCashShiftRequest request) {
        return ResponseEntity.ok(cashShiftService.close(principal.userId(), shiftId, request));
    }

    @Operation(summary = "List cash shifts",
            description = "Without BILLING_CASH_READ_ALL only the authenticated user's shifts are returned.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('BILLING_CASH_MANAGE', 'BILLING_CASH_READ_ALL')")
    public ResponseEntity<Page<CashShiftResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cashShiftService.listShifts(
                principal.userId(), canReadAll(principal), pageable(page, size)));
    }

    @Operation(summary = "Register a manual income or expense on an open cash shift")
    @PostMapping("/{shiftId}/movements")
    @PreAuthorize("hasAuthority('BILLING_CASH_MANAGE')")
    public ResponseEntity<CashMovementResponse> addMovement(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID shiftId,
            @Valid @RequestBody CreateCashMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashShiftService.addMovement(principal.userId(), shiftId, request));
    }

    @Operation(summary = "List manual movements of a cash shift")
    @GetMapping("/{shiftId}/movements")
    @PreAuthorize("hasAnyAuthority('BILLING_CASH_MANAGE', 'BILLING_CASH_READ_ALL')")
    public ResponseEntity<Page<CashMovementResponse>> listMovements(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID shiftId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cashShiftService.listMovements(
                principal.userId(), canReadAll(principal), shiftId, pageable(page, size)));
    }

    private boolean canReadAll(AuthenticatedUser principal) {
        return principal.authorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("BILLING_CASH_READ_ALL"::equals);
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
        return PageRequest.of(page, size);
    }
}
