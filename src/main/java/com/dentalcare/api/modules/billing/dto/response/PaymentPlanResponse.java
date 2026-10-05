package com.dentalcare.api.modules.billing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentPlanResponse(
        UUID id,
        UUID chargeId,
        UUID patientId,
        PaymentPlanViewStatus status,
        int installmentsCount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        LocalDate firstDueDate,
        Instant createdAt,
        UUID createdByUserId,
        Instant cancelledAt,
        UUID cancelledByUserId,
        String cancelReason,
        List<InstallmentResponse> installments) {
}
