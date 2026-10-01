package com.dentalcare.api.modules.reports.service;

import com.dentalcare.api.modules.reports.dto.response.DashboardReportResponse;

import java.time.LocalDate;

public interface DashboardReportService {

    DashboardReportResponse getDashboard(LocalDate from, LocalDate to);
}
