package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.InstallmentResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanViewStatus;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PaymentPlanMapper {

    public PaymentPlanResponse toResponse(PaymentPlan plan, PaymentPlanViewStatus status, BigDecimal paidAmount,
                                          BigDecimal pendingAmount, List<InstallmentResponse> installments) {
        return new PaymentPlanResponse(
                plan.getId(),
                plan.getChargeId(),
                plan.getPatientId(),
                status,
                plan.getInstallmentsCount(),
                plan.getTotalAmount(),
                paidAmount,
                pendingAmount,
                plan.getFirstDueDate(),
                plan.getCreatedAt(),
                plan.getCreatedByUserId(),
                plan.getCancelledAt(),
                plan.getCancelledByUserId(),
                plan.getCancelReason(),
                installments);
    }
}
