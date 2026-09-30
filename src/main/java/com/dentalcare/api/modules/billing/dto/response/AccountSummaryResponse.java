package com.dentalcare.api.modules.billing.dto.response;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        BigDecimal charged,
        BigDecimal paid,
        BigDecimal advances,
        BigDecimal balance) {
}
