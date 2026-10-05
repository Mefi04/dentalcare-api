package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.request.CreateRefundRequest;
import com.dentalcare.api.modules.billing.dto.response.RefundResponse;

import java.util.List;
import java.util.UUID;

public interface RefundService {

    RefundResult create(UUID patientId, UUID paymentId, UUID actorUserId, CreateRefundRequest request,
                        String idempotencyKey);

    List<RefundResponse> list(UUID patientId);
}
