package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.request.CreateChargeDiscountRequest;
import com.dentalcare.api.modules.billing.dto.request.VoidChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;

import java.util.List;
import java.util.UUID;

public interface ChargeAdjustmentService {

    ChargeAdjustmentResponse discount(UUID patientId, UUID chargeId, UUID actorUserId, CreateChargeDiscountRequest request);

    ChargeAdjustmentResponse voidCharge(UUID patientId, UUID chargeId, UUID actorUserId, VoidChargeRequest request);

    List<ChargeAdjustmentResponse> list(UUID patientId);
}
