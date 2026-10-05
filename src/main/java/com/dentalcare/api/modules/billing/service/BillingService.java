package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;

import java.util.UUID;

public interface BillingService {

    AccountStatementResponse findAccountStatement(UUID patientId);

    AccountStatementResponse findAccountStatementForAuthenticatedPatient(UUID authenticatedUserId);

    ChargeResponse createCharge(UUID patientId, CreateChargeRequest request);

    PaymentResponse registerPayment(UUID patientId, CreatePaymentRequest request, UUID actorUserId);
}
