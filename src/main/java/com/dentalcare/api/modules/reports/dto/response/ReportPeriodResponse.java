package com.dentalcare.api.modules.reports.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record ReportPeriodResponse(
        LocalDate from,
        LocalDate to,
        Instant generatedAt) {
}
