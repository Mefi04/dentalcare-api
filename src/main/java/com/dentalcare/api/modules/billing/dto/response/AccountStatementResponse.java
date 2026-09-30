package com.dentalcare.api.modules.billing.dto.response;

import java.util.List;
import java.util.UUID;

public record AccountStatementResponse(
        UUID patientId,
        AccountSummaryResponse summary,
        List<ChargeResponse> charges,
        List<PaymentResponse> payments) {
}
