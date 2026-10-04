package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeDiscountRequest;
import com.dentalcare.api.modules.billing.dto.request.VoidChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;
import com.dentalcare.api.modules.billing.ledger.ChargeLedger;
import com.dentalcare.api.modules.billing.ledger.ChargePosition;
import com.dentalcare.api.modules.billing.mapper.ChargeAdjustmentMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ChargeAdjustmentServiceImpl implements ChargeAdjustmentService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final String VOID_INDEX = "uq_billing_charge_adjustments_void_per_charge";
    private static final String CANCEL_PLAN = "Cancel the payment plan first";

    private final ChargeRepository chargeRepository;
    private final ChargeAdjustmentRepository chargeAdjustmentRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final ChargeLedger chargeLedger;
    private final ChargeAdjustmentMapper chargeAdjustmentMapper;
    private final Clock clock;

    public ChargeAdjustmentServiceImpl(ChargeRepository chargeRepository,
                                       ChargeAdjustmentRepository chargeAdjustmentRepository,
                                       PaymentRepository paymentRepository,
                                       RefundRepository refundRepository,
                                       PaymentPlanRepository paymentPlanRepository,
                                       ChargeLedger chargeLedger,
                                       ChargeAdjustmentMapper chargeAdjustmentMapper,
                                       Clock clock) {
        this.chargeRepository = chargeRepository;
        this.chargeAdjustmentRepository = chargeAdjustmentRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentPlanRepository = paymentPlanRepository;
        this.chargeLedger = chargeLedger;
        this.chargeAdjustmentMapper = chargeAdjustmentMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChargeAdjustmentResponse discount(UUID patientId, UUID chargeId, UUID actorUserId,
                                             CreateChargeDiscountRequest request) {
        requireActor(actorUserId);
        if (patientId == null || chargeId == null) {
            throw new BadRequestException("Patient id and charge id are required");
        }
        if (request == null) {
            throw new BadRequestException("Discount is required");
        }
        BigDecimal amount = normalizeAmount(request.amount());
        String reason = requireReason(request.reason());
        Charge charge = lockCharge(chargeId, patientId);
        rejectActivePlan(charge.getId(), patientId);
        ChargePosition position = positionOf(charge);
        if (position.voided()) {
            throw new ConflictException("Charge is voided");
        }
        if (position.pending().signum() <= 0) {
            throw new ConflictException("Charge has no pending balance");
        }
        if (amount.compareTo(position.pending()) > 0) {
            throw new ConflictException("Discount exceeds the pending balance");
        }
        ChargeAdjustment adjustment = save(charge, ChargeAdjustmentType.DISCOUNT, amount, reason, actorUserId);
        // TODO(#114): publicar evento de auditoría
        return chargeAdjustmentMapper.toResponse(adjustment);
    }

    @Override
    @Transactional
    public ChargeAdjustmentResponse voidCharge(UUID patientId, UUID chargeId, UUID actorUserId,
                                               VoidChargeRequest request) {
        requireActor(actorUserId);
        if (patientId == null || chargeId == null) {
            throw new BadRequestException("Patient id and charge id are required");
        }
        if (request == null) {
            throw new BadRequestException("Void is required");
        }
        String reason = requireReason(request.reason());
        Charge charge = lockCharge(chargeId, patientId);
        rejectActivePlan(charge.getId(), patientId);
        ChargePosition position = positionOf(charge);
        if (position.voided()) {
            throw new ConflictException("Charge is already voided");
        }
        if (position.netPaid().signum() != 0) {
            throw new ConflictException("Charge has a remaining paid balance");
        }
        try {
            ChargeAdjustment adjustment = save(charge, ChargeAdjustmentType.VOID, null, reason, actorUserId);
            // TODO(#114): publicar evento de auditoría
            return chargeAdjustmentMapper.toResponse(adjustment);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateVoid(exception)) {
                throw new ConflictException("Charge is already voided");
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargeAdjustmentResponse> list(UUID patientId) {
        if (patientId == null) {
            throw new BadRequestException("Patient id is required");
        }
        return chargeAdjustmentRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patientId).stream()
                .map(chargeAdjustmentMapper::toResponse)
                .toList();
    }

    private ChargeAdjustment save(Charge charge, ChargeAdjustmentType type, BigDecimal amount, String reason,
                                  UUID actorUserId) {
        return chargeAdjustmentRepository.saveAndFlush(new ChargeAdjustment(
                UUID.randomUUID(), charge.getId(), charge.getPatient().getId(), type, amount, reason,
                actorUserId, actorUserId, clock.instant()));
    }

    private Charge lockCharge(UUID chargeId, UUID patientId) {
        return chargeRepository.findByIdAndPatientIdForUpdate(chargeId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
    }

    private void rejectActivePlan(UUID chargeId, UUID patientId) {
        if (paymentPlanRepository.findActiveByChargeIdAndPatientId(chargeId, patientId).isPresent()) {
            throw new ConflictException(CANCEL_PLAN);
        }
    }

    private ChargePosition positionOf(Charge charge) {
        return chargeLedger.position(
                charge.getAmount(),
                paymentRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.sumDiscountByChargeId(charge.getId()),
                refundRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.existsByChargeIdAndType(charge.getId(), ChargeAdjustmentType.VOID));
    }

    private void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
    }

    private String requireReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("Reason is required");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        return reason;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BadRequestException("Amount must not have more than " + MONEY_SCALE + " decimals");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestException("Amount must not exceed " + MAX_AMOUNT);
        }
        return amount.setScale(MONEY_SCALE);
    }

    private boolean isDuplicateVoid(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().contains(VOID_INDEX)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(VOID_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
