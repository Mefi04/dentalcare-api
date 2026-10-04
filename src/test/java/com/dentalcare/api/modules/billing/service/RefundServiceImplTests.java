package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateRefundRequest;
import com.dentalcare.api.modules.billing.mapper.RefundMapper;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import com.dentalcare.api.modules.billing.model.Receipt;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;
import com.dentalcare.api.modules.billing.model.Refund;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.ReceiptRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ChargeRepository chargeRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private PaymentPlanRepository paymentPlanRepository;
    @Mock
    private CashShiftService cashShiftService;

    private RefundService service;
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(paymentRepository, chargeRepository, refundRepository, receiptRepository,
                paymentPlanRepository, cashShiftService, new RefundMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
                directTransactions());
        lenient().when(refundRepository.sumAmountByPaymentId(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(refundRepository.findByRequestedByUserIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(refundRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(receiptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void partialRefundLeavesTheReceiptIssued() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CARD, "40.00");
        stubPayment(payment, patient);

        RefundResult result = service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("10.00"), "Parcial"), null);

        assertThat(result.replay()).isFalse();
        assertThat(result.response().amount()).isEqualByComparingTo("10.00");
        assertThat(result.response().cashShiftId()).isNull();
        verify(receiptRepository, never()).findByPaymentId(any());
        verify(receiptRepository, never()).saveAndFlush(any());
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
    }

    @Test
    void fullRefundVoidsTheIssuedReceipt() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.TRANSFER, "40.00");
        Receipt receipt = receipt(payment, patient);
        stubPayment(payment, patient);
        when(receiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(receipt));

        service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("40.00"), "Total"), null);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.VOID);
        assertThat(receipt.getVoidReason()).isEqualTo("Total");
        assertThat(receipt.getVoidedAt()).isEqualTo(NOW);
    }

    @Test
    void refundAboveTheRemainingBalanceIsConflict() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CARD, "40.00");
        stubPayment(payment, patient);
        when(refundRepository.sumAmountByPaymentId(payment.getId())).thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("10.01"), "De mas"), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Refund amount exceeds the refundable balance of the payment");

        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void advanceRefundDoesNotRequireAShift() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CARD, "20.00");
        stubPayment(payment, patient);

        RefundResult result = service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("5.00"), "Anticipo"), null);

        assertThat(result.response().paymentId()).isEqualTo(payment.getId());
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
    }

    @Test
    void cashRefundWithoutOpenShiftDoesNotPersist() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "20.00");
        stubPayment(payment, patient);
        when(cashShiftService.requireOpenShiftForUpdate(actorId))
                .thenThrow(new ConflictException("No open cash shift"));

        assertThatThrownBy(() -> service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("5.00"), "Sin turno"), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No open cash shift");

        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void cashRefundStoresTheOpenShift() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "20.00");
        CashShift shift = new CashShift(UUID.randomUUID(), actorId, new BigDecimal("100.00"), null, NOW);
        stubPayment(payment, patient);
        when(cashShiftService.requireOpenShiftForUpdate(actorId)).thenReturn(shift);

        service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("5.00"), "Efectivo"), null);

        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCashShiftId()).isEqualTo(shift.getId());
        assertThat(saved.getValue().getRequestedByUserId()).isEqualTo(actorId);
        assertThat(saved.getValue().getAuthorizedByUserId()).isEqualTo(actorId);
    }

    @Test
    void refundOfAChargeWithAnActivePlanIsConflict() {
        Patient patient = patient();
        Charge charge = new Charge(UUID.randomUUID(), patient, "Limpieza", new BigDecimal("40.00"), NOW);
        Payment payment = payment(patient, charge, PaymentKind.PAYMENT, PaymentMethod.CARD, "40.00");
        stubPayment(payment, patient);
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(PaymentPlan.class)));

        assertThatThrownBy(() -> service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("5.00"), "Convenio"), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cancel the payment plan first");

        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void paymentOfAnotherPatientIsNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndPatientId(paymentId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(patientId, paymentId, actorId,
                new CreateRefundRequest(new BigDecimal("1.00"), "Ajeno"), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment not found");

        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalRefundWithoutSideEffects() {
        Patient patient = patient();
        UUID paymentId = UUID.randomUUID();
        Refund existing = new Refund(UUID.randomUUID(), paymentId, patient.getId(), new BigDecimal("10.00"), "Motivo",
                actorId, actorId, null, "key-1", NOW);
        when(refundRepository.findByRequestedByUserIdAndIdempotencyKey(actorId, "key-1"))
                .thenReturn(Optional.of(existing));

        RefundResult result = service.create(patient.getId(), paymentId, actorId,
                new CreateRefundRequest(new BigDecimal("10.00"), " Motivo "), "key-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.response().id()).isEqualTo(existing.getId());
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
        verify(refundRepository, never()).saveAndFlush(any());
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmountIsConflict() {
        Patient patient = patient();
        UUID paymentId = UUID.randomUUID();
        Refund existing = new Refund(UUID.randomUUID(), paymentId, patient.getId(), new BigDecimal("10.00"), "Motivo",
                actorId, actorId, null, "key-1", NOW);
        when(refundRepository.findByRequestedByUserIdAndIdempotencyKey(actorId, "key-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(patient.getId(), paymentId, actorId,
                new CreateRefundRequest(new BigDecimal("11.00"), "Motivo"), "key-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency key was already used with different data");

        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void idempotencyRaceReturnsTheOriginalRefund() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CARD, "40.00");
        Refund existing = new Refund(UUID.randomUUID(), payment.getId(), patient.getId(), new BigDecimal("10.00"),
                "Motivo", actorId, actorId, null, "key-1", NOW);
        stubPayment(payment, patient);
        when(refundRepository.findByRequestedByUserIdAndIdempotencyKey(actorId, "key-1"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(existing));
        when(refundRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key uq_billing_refunds_idempotency"));

        RefundResult result = service.create(patient.getId(), payment.getId(), actorId,
                new CreateRefundRequest(new BigDecimal("10.00"), "Motivo"), "key-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.response().id()).isEqualTo(existing.getId());
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void blankIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), UUID.randomUUID(), actorId,
                new CreateRefundRequest(new BigDecimal("1.00"), "Motivo"), " "))
                .isInstanceOf(BadRequestException.class);
    }

    private void stubPayment(Payment payment, Patient patient) {
        when(paymentRepository.findByIdAndPatientId(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        lenient().when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId()))
                .thenReturn(Optional.of(payment));
    }

    private Receipt receipt(Payment payment, Patient patient) {
        return new Receipt(UUID.randomUUID(), 8L, payment.getId(), patient.getId(), "Anticipo",
                payment.getAmount(), payment.getMethod(), actorId, NOW.minusSeconds(5));
    }

    private Payment payment(Patient patient, Charge charge, PaymentKind kind, PaymentMethod method, String amount) {
        return new Payment(UUID.randomUUID(), patient, charge, kind, method, new BigDecimal(amount), NOW);
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        return patient;
    }

    private static PlatformTransactionManager directTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
