package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CancelPaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.response.InstallmentResponse;
import com.dentalcare.api.modules.billing.dto.response.InstallmentStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanViewStatus;
import com.dentalcare.api.modules.billing.ledger.ChargeLedger;
import com.dentalcare.api.modules.billing.ledger.ChargePosition;
import com.dentalcare.api.modules.billing.mapper.PaymentPlanMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.model.Installment;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import com.dentalcare.api.modules.billing.model.PaymentPlanStatus;
import com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentPlanServiceImpl implements PaymentPlanService {

    private static final int MIN_INSTALLMENTS = 2;
    private static final int MAX_INSTALLMENTS = 60;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private static final String ACTIVE_PLAN_INDEX = "uq_billing_payment_plans_active_charge";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");

    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeAdjustmentRepository chargeAdjustmentRepository;
    private final RefundRepository refundRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final PaymentPlanMapper paymentPlanMapper;
    private final ChargeLedger chargeLedger;
    private final Clock clock;

    public PaymentPlanServiceImpl(ChargeRepository chargeRepository, PaymentRepository paymentRepository,
                                  ChargeAdjustmentRepository chargeAdjustmentRepository,
                                  RefundRepository refundRepository,
                                  PaymentPlanRepository paymentPlanRepository, PaymentPlanMapper paymentPlanMapper,
                                  ChargeLedger chargeLedger, Clock clock) {
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.chargeAdjustmentRepository = chargeAdjustmentRepository;
        this.refundRepository = refundRepository;
        this.paymentPlanRepository = paymentPlanRepository;
        this.paymentPlanMapper = paymentPlanMapper;
        this.chargeLedger = chargeLedger;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentPlanResponse create(UUID patientId, UUID chargeId, UUID actorUserId, CreatePaymentPlanRequest request) {
        requireActor(actorUserId);
        if (patientId == null || chargeId == null) {
            throw new BadRequestException("Patient id and charge id are required");
        }
        if (request == null) {
            throw new BadRequestException("Payment plan is required");
        }
        int installmentsCount = requireInstallmentsCount(request.installmentsCount());
        LocalDate firstDueDate = requireFirstDueDate(request.firstDueDate());

        Charge charge = chargeRepository.findByIdAndPatientIdForUpdate(chargeId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        if (paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patientId).isPresent()) {
            throw new ConflictException("An active payment plan already exists for this charge");
        }

        ChargePosition position = chargeLedger.position(
                charge.getAmount(),
                paymentRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.sumDiscountByChargeId(charge.getId()),
                refundRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.existsByChargeIdAndType(charge.getId(), ChargeAdjustmentType.VOID));
        if (position.voided()) {
            throw new ConflictException("Charge is voided");
        }
        BigDecimal baselinePaid = position.netPaid();
        BigDecimal totalAmount = position.pending();
        if (totalAmount.signum() <= 0) {
            throw new ConflictException("Charge has no pending balance");
        }

        PaymentPlan plan = new PaymentPlan(
                UUID.randomUUID(), charge.getId(), charge.getPatient().getId(), installmentsCount,
                totalAmount, baselinePaid, firstDueDate, actorUserId, clock.instant());
        addInstallments(plan, totalAmount, installmentsCount, firstDueDate);
        try {
            paymentPlanRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateActivePlan(exception)) {
                throw new ConflictException("An active payment plan already exists for this charge");
            }
            throw exception;
        }
        // TODO(#114): publicar evento de auditoría
        return toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentPlanResponse findActiveByCharge(UUID patientId, UUID chargeId) {
        if (patientId == null || chargeId == null) {
            throw new BadRequestException("Patient id and charge id are required");
        }
        PaymentPlan plan = paymentPlanRepository.findActiveByChargeIdAndPatientId(chargeId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan not found"));
        return toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PaymentPlanResponse> list(UUID patientId) {
        if (patientId == null) {
            throw new BadRequestException("Patient id is required");
        }
        return paymentPlanRepository.findByPatientIdOrderByCreatedAtDescIdDesc(patientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaymentPlanResponse cancel(UUID patientId, UUID planId, UUID actorUserId, CancelPaymentPlanRequest request) {
        requireActor(actorUserId);
        if (patientId == null || planId == null) {
            throw new BadRequestException("Patient id and payment plan id are required");
        }
        String reason = requireReason(request == null ? null : request.reason());
        PaymentPlan plan = paymentPlanRepository.findByIdAndPatientIdForUpdate(planId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan not found"));
        if (plan.getStatus() == PaymentPlanStatus.CANCELLED) {
            throw new ConflictException("Payment plan is already cancelled");
        }
        plan.cancel(actorUserId, reason, clock.instant());
        paymentPlanRepository.saveAndFlush(plan);
        // TODO(#114): publicar evento de auditoría
        return toResponse(plan);
    }

    private void addInstallments(PaymentPlan plan, BigDecimal totalAmount, int installmentsCount, LocalDate firstDueDate) {
        BigDecimal base = totalAmount.divide(BigDecimal.valueOf(installmentsCount), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal last = totalAmount.subtract(base.multiply(BigDecimal.valueOf(installmentsCount - 1)));
        if (base.signum() <= 0 || last.signum() <= 0) {
            throw new BadRequestException("Pending balance cannot be split into that many installments");
        }
        for (int index = 0; index < installmentsCount; index++) {
            BigDecimal amount = index == installmentsCount - 1 ? last : base;
            plan.addInstallment(new Installment(
                    UUID.randomUUID(), index + 1, amount, firstDueDate.plusMonths(index)));
        }
    }

    private PaymentPlanResponse toResponse(PaymentPlan plan) {
        BigDecimal paidOnCharge = chargeLedger.netPaid(
                paymentRepository.sumAmountByChargeId(plan.getChargeId()),
                refundRepository.sumAmountByChargeId(plan.getChargeId()));
        BigDecimal paidSincePlan = paidOnCharge.subtract(scale(plan.getBaselinePaid()));
        if (paidSincePlan.signum() < 0) {
            paidSincePlan = ZERO;
        }
        BigDecimal totalAmount = scale(plan.getTotalAmount());
        BigDecimal paidAmount = paidSincePlan.min(totalAmount);
        BigDecimal pendingAmount = totalAmount.subtract(paidAmount);
        LocalDate today = today();

        BigDecimal remaining = paidSincePlan;
        List<InstallmentResponse> installments = new ArrayList<>();
        boolean allPaid = true;
        for (Installment installment : plan.getInstallments()) {
            BigDecimal amount = scale(installment.getAmount());
            BigDecimal applied = remaining.min(amount);
            if (applied.signum() < 0) {
                applied = ZERO;
            }
            remaining = remaining.subtract(applied);
            InstallmentStatus status;
            if (applied.compareTo(amount) >= 0) {
                status = InstallmentStatus.PAID;
                applied = amount;
            } else if (applied.signum() > 0) {
                status = InstallmentStatus.PARTIALLY_PAID;
                allPaid = false;
            } else {
                status = InstallmentStatus.PENDING;
                allPaid = false;
            }
            boolean overdue = installment.getDueDate().isBefore(today) && status != InstallmentStatus.PAID;
            installments.add(new InstallmentResponse(
                    installment.getNumber(), amount, applied, installment.getDueDate(), status, overdue));
        }

        PaymentPlanViewStatus status;
        if (plan.getStatus() == PaymentPlanStatus.CANCELLED) {
            status = PaymentPlanViewStatus.CANCELLED;
        } else if (allPaid) {
            status = PaymentPlanViewStatus.COMPLETED;
        } else {
            status = PaymentPlanViewStatus.ACTIVE;
        }
        return paymentPlanMapper.toResponse(plan, status, paidAmount, pendingAmount, installments);
    }

    private int requireInstallmentsCount(Integer installmentsCount) {
        if (installmentsCount == null || installmentsCount < MIN_INSTALLMENTS || installmentsCount > MAX_INSTALLMENTS) {
            throw new BadRequestException("Installments count must be between 2 and 60");
        }
        return installmentsCount;
    }

    private LocalDate requireFirstDueDate(LocalDate firstDueDate) {
        if (firstDueDate == null) {
            throw new BadRequestException("First due date is required");
        }
        if (firstDueDate.isBefore(today())) {
            throw new BadRequestException("First due date must be today or later");
        }
        return firstDueDate;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), CLINIC_ZONE);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Cancel reason is required");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Cancel reason must not exceed 500 characters");
        }
        return normalized;
    }

    private BigDecimal scale(BigDecimal amount) {
        return (amount == null ? ZERO : amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isDuplicateActivePlan(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(ACTIVE_PLAN_INDEX)) {
                return true;
            }
            if (current instanceof ConstraintViolationException violation
                    && ACTIVE_PLAN_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
    }
}
