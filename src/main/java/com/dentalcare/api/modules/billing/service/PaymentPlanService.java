package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.request.CancelPaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentPlanService {

    PaymentPlanResponse create(UUID patientId, UUID chargeId, UUID actorUserId, CreatePaymentPlanRequest request);

    PaymentPlanResponse findActiveByCharge(UUID patientId, UUID chargeId);

    List<PaymentPlanResponse> list(UUID patientId);

    PaymentPlanResponse cancel(UUID patientId, UUID planId, UUID actorUserId, CancelPaymentPlanRequest request);
}
