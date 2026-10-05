package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.response.RefundResponse;

public record RefundResult(RefundResponse response, boolean replay) {
}
