package com.dentalcare.api.modules.billing.ledger;

import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;

import java.math.BigDecimal;

public record ChargePosition(
        BigDecimal netPaid,
        BigDecimal discount,
        BigDecimal refunded,
        BigDecimal pending,
        ChargeStatus status,
        boolean voided) {
}
