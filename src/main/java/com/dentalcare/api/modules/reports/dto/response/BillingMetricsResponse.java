package com.dentalcare.api.modules.reports.dto.response;

import java.math.BigDecimal;

public record BillingMetricsResponse(
        BigDecimal chargesCreatedInPeriod,
        BigDecimal paymentsReceivedInPeriod,
        BigDecimal pendingBalance,
        BigDecimal availableCredit) {
}
