package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.mapper.ReceiptMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.Receipt;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.ReceiptRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    static final String ADVANCE_CONCEPT = "Anticipo";
    private static final String PAYMENT_RECEIPT_CONSTRAINT = "uq_billing_receipts_payment";

    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;
    private final RefundRepository refundRepository;
    private final ReceiptMapper receiptMapper;
    private final Clock clock;

    public ReceiptServiceImpl(PaymentRepository paymentRepository, ReceiptRepository receiptRepository,
                              RefundRepository refundRepository, ReceiptMapper receiptMapper, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.refundRepository = refundRepository;
        this.receiptMapper = receiptMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReceiptResponse issue(UUID patientId, UUID paymentId, UUID actorUserId) {
        requireActor(actorUserId);
        Payment payment = requirePaymentForIssue(patientId, paymentId);
        if (receiptRepository.existsByPaymentId(payment.getId())) {
            throw new ConflictException("A receipt has already been issued for this payment");
        }
        Receipt receipt = new Receipt(
                UUID.randomUUID(),
                nextReceiptNumber(),
                payment.getId(),
                payment.getPatient().getId(),
                conceptOf(payment),
                payment.getAmount(),
                payment.getMethod(),
                actorUserId,
                clock.instant());
        try {
            return receiptMapper.toResponse(receiptRepository.saveAndFlush(receipt));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicatePaymentReceipt(exception)) {
                throw new ConflictException("A receipt has already been issued for this payment");
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse findByPayment(UUID patientId, UUID paymentId) {
        Payment payment = requirePayment(patientId, paymentId);
        Receipt receipt = receiptRepository.findByPaymentId(payment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
        return receiptMapper.toResponse(receipt);
    }

    private Payment requirePaymentForIssue(UUID patientId, UUID paymentId) {
        Payment payment = requirePayment(patientId, paymentId, true);
        BigDecimal refunded = refundRepository.sumAmountByPaymentId(payment.getId());
        if (refunded != null && refunded.compareTo(payment.getAmount()) >= 0) {
            throw new ConflictException("Payment is fully refunded");
        }
        return payment;
    }

    private Payment requirePayment(UUID patientId, UUID paymentId) {
        return requirePayment(patientId, paymentId, false);
    }

    private Payment requirePayment(UUID patientId, UUID paymentId, boolean lock) {
        if (patientId == null || paymentId == null) {
            throw new BadRequestException("Patient id and payment id are required");
        }
        return (lock
                ? paymentRepository.findByIdAndPatientIdForUpdate(paymentId, patientId)
                : paymentRepository.findByIdAndPatientId(paymentId, patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private String conceptOf(Payment payment) {
        Charge charge = payment.getCharge();
        if (charge != null) {
            return charge.getConcept();
        }
        if (payment.getKind() == PaymentKind.ADVANCE) {
            return ADVANCE_CONCEPT;
        }
        throw new ConflictException("Payment cannot be receipted");
    }

    private long nextReceiptNumber() {
        Number number = receiptRepository.nextReceiptNumber();
        if (number == null) {
            throw new IllegalStateException("Receipt number sequence returned no value");
        }
        return number.longValue();
    }

    private boolean isDuplicatePaymentReceipt(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(PAYMENT_RECEIPT_CONSTRAINT)) {
                return true;
            }
            if (current instanceof ConstraintViolationException violation
                    && PAYMENT_RECEIPT_CONSTRAINT.equals(violation.getConstraintName())) {
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
