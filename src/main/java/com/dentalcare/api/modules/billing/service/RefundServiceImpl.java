package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.billing.dto.request.CreateRefundRequest;
import com.dentalcare.api.modules.billing.dto.response.RefundResponse;
import com.dentalcare.api.modules.billing.mapper.RefundMapper;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;
import com.dentalcare.api.modules.billing.model.Refund;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.ReceiptRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefundServiceImpl implements RefundService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final String IDEMPOTENCY_CONSTRAINT = "uq_billing_refunds_idempotency";
    private static final String CANCEL_PLAN = "Cancel the payment plan first";

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final RefundRepository refundRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final CashShiftService cashShiftService;
    private final RefundMapper refundMapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Autowired
    public RefundServiceImpl(PaymentRepository paymentRepository,
                             ChargeRepository chargeRepository,
                             RefundRepository refundRepository,
                             ReceiptRepository receiptRepository,
                             PaymentPlanRepository paymentPlanRepository,
                             CashShiftService cashShiftService,
                             RefundMapper refundMapper,
                             Clock clock,
                             @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.chargeRepository = chargeRepository;
        this.refundRepository = refundRepository;
        this.receiptRepository = receiptRepository;
        this.paymentPlanRepository = paymentPlanRepository;
        this.cashShiftService = cashShiftService;
        this.refundMapper = refundMapper;
        this.clock = clock;
        this.transactions = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Override
    public RefundResult create(UUID patientId, UUID paymentId, UUID actorUserId, CreateRefundRequest request,
                               String idempotencyKey) {
        requireActor(actorUserId);
        if (patientId == null || paymentId == null) {
            throw new BadRequestException("Patient id and payment id are required");
        }
        if (request == null) {
            throw new BadRequestException("Refund is required");
        }
        BigDecimal amount = normalizeAmount(request.amount());
        String reason = requireReason(request.reason());
        String key = normalizeKey(idempotencyKey);
        try {
            if (transactions == null) {
                return write(patientId, paymentId, actorUserId, amount, reason, key);
            }
            return transactions.execute(status -> write(patientId, paymentId, actorUserId, amount, reason, key));
        } catch (IdempotencyRaceException race) {
            return replayAfterRace(race);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> list(UUID patientId) {
        if (patientId == null) {
            throw new BadRequestException("Patient id is required");
        }
        return refundRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patientId).stream()
                .map(refundMapper::toResponse)
                .toList();
    }

    private RefundResult write(UUID patientId, UUID paymentId, UUID actorUserId, BigDecimal amount, String reason,
                               String key) {
        if (key != null) {
            Optional<Refund> existing = refundRepository.findByRequestedByUserIdAndIdempotencyKey(actorUserId, key);
            if (existing.isPresent()) {
                return replay(existing.get(), patientId, paymentId, amount, reason);
            }
        }

        Payment preview = paymentRepository.findByIdAndPatientId(paymentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Lock order is the open shift, then the charge, then the payment. Close locks only the
        // shift, and a cash payment locks the shift before the charge, so this order does not cycle.
        CashShift cashShift = null;
        if (preview.getMethod() == PaymentMethod.CASH) {
            cashShift = cashShiftService.requireOpenShiftForUpdate(actorUserId);
        }
        Charge charge = preview.getCharge();
        if (charge != null) {
            Charge lockedCharge = chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
            if (paymentPlanRepository.findActiveByChargeIdAndPatientId(lockedCharge.getId(), patientId).isPresent()) {
                throw new ConflictException(CANCEL_PLAN);
            }
        }
        Payment payment = paymentRepository.findByIdAndPatientIdForUpdate(paymentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (key != null) {
            Optional<Refund> existing = refundRepository.findByRequestedByUserIdAndIdempotencyKey(actorUserId, key);
            if (existing.isPresent()) {
                return replay(existing.get(), patientId, paymentId, amount, reason);
            }
        }

        BigDecimal alreadyRefunded = scale(refundRepository.sumAmountByPaymentId(payment.getId()));
        if (amount.compareTo(payment.getAmount().subtract(alreadyRefunded)) > 0) {
            throw new ConflictException("Refund amount exceeds the refundable balance of the payment");
        }
        UUID cashShiftId = payment.getMethod() == PaymentMethod.CASH && cashShift != null ? cashShift.getId() : null;
        Refund refund = new Refund(
                UUID.randomUUID(), payment.getId(), payment.getPatient().getId(), amount, reason,
                actorUserId, actorUserId, cashShiftId, key, clock.instant());
        try {
            refundRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException exception) {
            if (isIdempotencyViolation(exception)) {
                throw new IdempotencyRaceException(actorUserId, key, patientId, paymentId, amount, reason);
            }
            throw exception;
        }
        voidReceiptIfFullyRefunded(payment, alreadyRefunded.add(amount), reason);
        if (auditService != null) auditService.success(AuditActions.BILLING_REFUND_CREATED, "BILLING", "Refund", refund.getId(), actorUserId);
        return new RefundResult(refundMapper.toResponse(refund), false);
    }

    private RefundResult replayAfterRace(IdempotencyRaceException race) {
        Refund existing = refundRepository.findByRequestedByUserIdAndIdempotencyKey(race.actorUserId(), race.key())
                .orElseThrow(() -> new ConflictException("Idempotency key was already used with different data"));
        return replay(existing, race.patientId(), race.paymentId(), race.amount(), race.reason());
    }

    private RefundResult replay(Refund existing, UUID patientId, UUID paymentId, BigDecimal amount, String reason) {
        boolean same = existing.getPatientId().equals(patientId)
                && existing.getPaymentId().equals(paymentId)
                && existing.getAmount().compareTo(amount) == 0
                && existing.getReason().equals(reason);
        if (!same) {
            throw new ConflictException("Idempotency key was already used with different data");
        }
        return new RefundResult(refundMapper.toResponse(existing), true);
    }

    private void voidReceiptIfFullyRefunded(Payment payment, BigDecimal refunded, String reason) {
        if (refunded.compareTo(payment.getAmount()) != 0) {
            return;
        }
        receiptRepository.findByPaymentId(payment.getId()).ifPresent(receipt -> {
            if (receipt.getStatus() == ReceiptStatus.ISSUED) {
                receipt.voidReceipt(clock.instant(), reason);
                receiptRepository.saveAndFlush(receipt);
            }
        });
    }

    private void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        if (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException(
                    "Idempotency key must not be blank and must not exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
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

    private BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE);
    }

    private boolean isIdempotencyViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class IdempotencyRaceException extends RuntimeException {
        private final UUID actorUserId;
        private final String key;
        private final UUID patientId;
        private final UUID paymentId;
        private final BigDecimal amount;
        private final String reason;

        private IdempotencyRaceException(UUID actorUserId, String key, UUID patientId, UUID paymentId,
                                         BigDecimal amount, String reason) {
            this.actorUserId = actorUserId;
            this.key = key;
            this.patientId = patientId;
            this.paymentId = paymentId;
            this.amount = amount;
            this.reason = reason;
        }

        private UUID actorUserId() { return actorUserId; }
        private String key() { return key; }
        private UUID patientId() { return patientId; }
        private UUID paymentId() { return paymentId; }
        private BigDecimal amount() { return amount; }
        private String reason() { return reason; }
    }
}
