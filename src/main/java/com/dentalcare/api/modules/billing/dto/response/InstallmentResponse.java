package com.dentalcare.api.modules.billing.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentResponse(
        int number,
        BigDecimal amount,
        BigDecimal paidAmount,
        LocalDate dueDate,
        InstallmentStatus status,
        boolean overdue) {
}
