package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.model.CashMovement;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.CashShiftStatus;
import com.dentalcare.api.modules.billing.repository.CashMovementRepository;
import com.dentalcare.api.modules.billing.repository.CashShiftRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class CashShiftServiceImpl implements CashShiftService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONCEPT_LENGTH = 200;
    private static final int MAX_NOTES_LENGTH = 500;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final String OPEN_SHIFT_INDEX = "uq_billing_cash_shifts_open_per_user";
    private static final Sort SHIFT_SORT = Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("id"));
    private static final Sort MOVEMENT_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final CashShiftRepository cashShiftRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final CashShiftMapper cashShiftMapper;
    private final Clock clock;

    public CashShiftServiceImpl(CashShiftRepository cashShiftRepository,
                                CashMovementRepository cashMovementRepository,
                                PaymentRepository paymentRepository,
                                CashShiftMapper cashShiftMapper,
                                Clock clock) {
        this.cashShiftRepository = cashShiftRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.paymentRepository = paymentRepository;
        this.cashShiftMapper = cashShiftMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CashShiftResponse getCurrent(UUID actorId) {
        requireActor(actorId);
        CashShift shift = cashShiftRepository.findOpenByUserId(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found"));
        return toResponse(shift);
    }

    @Override
    @Transactional
    public CashShiftResponse open(UUID actorId, OpenCashShiftRequest request) {
        requireActor(actorId);
        if (request == null) {
            throw new BadRequestException("Cash shift is required");
        }
        BigDecimal openingAmount = normalizeMoney(request.openingAmount(), false, "Opening amount");
        String notes = normalizeNotes(request.notes());
        if (cashShiftRepository.findOpenByUserId(actorId).isPresent()) {
            throw new ConflictException("A cash shift is already open");
        }

        CashShift shift = new CashShift(UUID.randomUUID(), actorId, openingAmount, notes, clock.instant());
        try {
            cashShiftRepository.saveAndFlush(shift);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateOpenShift(exception)) {
                throw new ConflictException("A cash shift is already open");
            }
            throw exception;
        }
        // TODO(#114): publicar evento de auditoría
        return toResponse(shift);
    }

    @Override
    @Transactional
    public CashShiftResponse close(UUID actorId, UUID shiftId, CloseCashShiftRequest request) {
        CashShift shift = requireOwnShiftForUpdate(actorId, shiftId);
        if (request == null) {
            throw new BadRequestException("Closing data is required");
        }
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new ConflictException("Cash shift is already closed");
        }
        BigDecimal countedAmount = normalizeMoney(request.countedAmount(), false, "Counted amount");
        String notes = normalizeNotes(request.notes());
        BigDecimal expectedAmount = calculateExpected(shift);
        BigDecimal difference = countedAmount.subtract(expectedAmount);
        shift.close(expectedAmount, countedAmount, difference, notes, clock.instant());
        cashShiftRepository.saveAndFlush(shift);
        // TODO(#114): publicar evento de auditoría
        return toResponse(shift);
    }

    @Override
    @Transactional
    public CashMovementResponse addMovement(UUID actorId, UUID shiftId, CreateCashMovementRequest request) {
        CashShift shift = requireOwnShiftForUpdate(actorId, shiftId);
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new ConflictException("Cash shift is not open");
        }
        if (request == null) {
            throw new BadRequestException("Cash movement is required");
        }
        if (request.type() == null) {
            throw new BadRequestException("Movement type is required");
        }
        BigDecimal amount = normalizeMoney(request.amount(), true, "Amount");
        String concept = normalizeConcept(request.concept());
        CashMovement movement = cashMovementRepository.saveAndFlush(new CashMovement(
                UUID.randomUUID(), shift, request.type(), amount, concept, actorId, clock.instant()));
        return cashShiftMapper.toResponse(movement);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashShiftResponse> listShifts(UUID actorId, boolean canReadAll, Pageable pageable) {
        requireActor(actorId);
        Pageable safePage = clamp(pageable, SHIFT_SORT);
        Page<CashShift> shifts = canReadAll
                ? cashShiftRepository.findAll(safePage)
                : cashShiftRepository.findByUserId(actorId, safePage);
        return shifts.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashMovementResponse> listMovements(UUID actorId, boolean canReadAll, UUID shiftId, Pageable pageable) {
        CashShift shift = requireVisibleShift(actorId, canReadAll, shiftId);
        return cashMovementRepository.findByCashShift_Id(shift.getId(), clamp(pageable, MOVEMENT_SORT))
                .map(cashShiftMapper::toResponse);
    }

    @Override
    @Transactional
    public CashShift requireOpenShiftForUpdate(UUID actorId) {
        requireActor(actorId);
        CashShift shift = cashShiftRepository.findOpenByUserIdForUpdate(actorId)
                .orElseThrow(() -> new ConflictException("No open cash shift"));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new ConflictException("No open cash shift");
        }
        return shift;
    }

    private CashShift requireOwnShiftForUpdate(UUID actorId, UUID shiftId) {
        requireActor(actorId);
        requireShiftId(shiftId);
        return cashShiftRepository.findByIdAndUserIdForUpdate(shiftId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found"));
    }

    private CashShift requireVisibleShift(UUID actorId, boolean canReadAll, UUID shiftId) {
        requireActor(actorId);
        requireShiftId(shiftId);
        CashShift shift = cashShiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found"));
        if (!canReadAll && !actorId.equals(shift.getUserId())) {
            throw new ResourceNotFoundException("Cash shift not found");
        }
        return shift;
    }

    private CashShiftResponse toResponse(CashShift shift) {
        BigDecimal expectedAmount = shift.getStatus() == CashShiftStatus.OPEN
                ? calculateExpected(shift)
                : shift.getExpectedAmount();
        return cashShiftMapper.toResponse(shift, expectedAmount);
    }

    private BigDecimal calculateExpected(CashShift shift) {
        BigDecimal cash = scale(paymentRepository.sumCashAmountByCashShiftId(shift.getId()));
        BigDecimal income = scale(cashMovementRepository.sumIncomeByCashShiftId(shift.getId()));
        BigDecimal expense = scale(cashMovementRepository.sumExpenseByCashShiftId(shift.getId()));
        return shift.getOpeningAmount().add(cash).add(income).subtract(expense);
    }

    private Pageable clamp(Pageable pageable, Sort defaultSort) {
        if (pageable == null) {
            throw new BadRequestException("Page is required");
        }
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), defaultSort);
    }

    private BigDecimal normalizeMoney(BigDecimal amount, boolean strictlyPositive, String label) {
        if (amount == null) {
            throw new BadRequestException(label + " is required");
        }
        if (strictlyPositive ? amount.signum() <= 0 : amount.signum() < 0) {
            throw new BadRequestException(strictlyPositive
                    ? "Amount must be greater than zero"
                    : label + " must be greater than or equal to zero");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BadRequestException(label + " must not have more than " + MONEY_SCALE + " decimals");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestException(label + " must not exceed " + MAX_AMOUNT);
        }
        return amount.setScale(MONEY_SCALE);
    }

    private String normalizeConcept(String value) {
        String concept = value == null ? "" : value.trim();
        if (concept.isEmpty()) {
            throw new BadRequestException("Concept is required");
        }
        if (concept.length() > MAX_CONCEPT_LENGTH) {
            throw new BadRequestException("Concept must not exceed " + MAX_CONCEPT_LENGTH + " characters");
        }
        return concept;
    }

    private String normalizeNotes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String notes = value.trim();
        if (notes.length() > MAX_NOTES_LENGTH) {
            throw new BadRequestException("Notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }
        return notes;
    }

    private BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE);
    }

    private boolean isDuplicateOpenShift(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains(OPEN_SHIFT_INDEX)
                    || (message.contains("duplicate key") && message.contains("billing_cash_shifts")))) {
                return true;
            }
            if (current instanceof ConstraintViolationException violation
                    && OPEN_SHIFT_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
    }

    private void requireShiftId(UUID shiftId) {
        if (shiftId == null) {
            throw new BadRequestException("Cash shift id is required");
        }
    }
}
