package com.dentalcare.api.modules.reports.repository;

import java.math.BigDecimal;

public record BillingMetricsSnapshot(
        BigDecimal chargesCreatedInPeriod,
        BigDecimal paymentsReceivedInPeriod,
        BigDecimal totalCharges,
        BigDecimal totalPayments) {
}
