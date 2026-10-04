package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.RefundResponse;
import com.dentalcare.api.modules.billing.model.Refund;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getPatientId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getRequestedByUserId(),
                refund.getAuthorizedByUserId(),
                refund.getCashShiftId(),
                refund.getCreatedAt());
    }
}
