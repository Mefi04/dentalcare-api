package com.dentalcare.api.modules.reports.controller;

import com.dentalcare.api.modules.reports.dto.response.DashboardReportResponse;
import com.dentalcare.api.modules.reports.service.DashboardReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Read-only administrative dashboard metrics")
public class DashboardReportController {

    private final DashboardReportService dashboardReportService;

    public DashboardReportController(DashboardReportService dashboardReportService) {
        this.dashboardReportService = dashboardReportService;
    }

    @Operation(summary = "Get real administrative dashboard metrics for a period",
            description = "from/to are inclusive LocalDate values interpreted as clinic operating days in "
                    + "America/Guatemala. Internally, the period is queried as the half-open interval "
                    + "[start of from, start of the day after to). Defaults to the current clinic month.")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'CASHIER')")
    public ResponseEntity<DashboardReportResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardReportService.getDashboard(from, to));
    }
}
