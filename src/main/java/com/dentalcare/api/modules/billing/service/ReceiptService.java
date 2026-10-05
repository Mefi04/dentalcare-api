package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;

import java.util.UUID;

public interface ReceiptService {

    ReceiptResponse issue(UUID patientId, UUID paymentId, UUID actorUserId);

    ReceiptResponse findByPayment(UUID patientId, UUID paymentId);
}
