package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.model.CashShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CashShiftService {

    CashShiftResponse getCurrent(UUID actorId);

    CashShiftResponse open(UUID actorId, OpenCashShiftRequest request);

    CashShiftResponse close(UUID actorId, UUID shiftId, CloseCashShiftRequest request);

    CashMovementResponse addMovement(UUID actorId, UUID shiftId, CreateCashMovementRequest request);

    Page<CashShiftResponse> listShifts(UUID actorId, boolean canReadAll, Pageable pageable);

    Page<CashMovementResponse> listMovements(UUID actorId, boolean canReadAll, UUID shiftId, Pageable pageable);

    /**
     * Locks the actor's open shift in the caller's transaction.
     * Cash registration must keep this lock until the payment is persisted.
     */
    CashShift requireOpenShiftForUpdate(UUID actorId);
}
