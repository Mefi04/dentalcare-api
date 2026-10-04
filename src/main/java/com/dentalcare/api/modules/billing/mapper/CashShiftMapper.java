package com.dentalcare.api.modules.billing.mapper;

import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.model.CashMovement;
import com.dentalcare.api.modules.billing.model.CashShift;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CashShiftMapper {

    public CashShiftResponse toResponse(CashShift shift, BigDecimal expectedAmount) {
        return new CashShiftResponse(
                shift.getId(),
                shift.getUserId(),
                shift.getStatus(),
                shift.getOpeningAmount(),
                shift.getOpenedAt(),
                shift.getClosedAt(),
                expectedAmount,
                shift.getCountedAmount(),
                shift.getDifference(),
                shift.getOpeningNotes(),
                shift.getClosingNotes());
    }

    public CashMovementResponse toResponse(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(),
                movement.getCashShift().getId(),
                movement.getType(),
                movement.getAmount(),
                movement.getConcept(),
                movement.getCreatedByUserId(),
                movement.getCreatedAt());
    }
}
