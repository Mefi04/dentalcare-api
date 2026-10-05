package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;
import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import org.springframework.stereotype.Component;

@Component
public class ChargeAdjustmentMapper {

    public ChargeAdjustmentResponse toResponse(ChargeAdjustment adjustment) {
        return new ChargeAdjustmentResponse(
                adjustment.getId(),
                adjustment.getChargeId(),
                adjustment.getPatientId(),
                adjustment.getType(),
                adjustment.getAmount(),
                adjustment.getReason(),
                adjustment.getRequestedByUserId(),
                adjustment.getAuthorizedByUserId(),
                adjustment.getCreatedAt());
    }
}
