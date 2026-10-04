package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CancelPaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.response.InstallmentStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanViewStatus;
import com.dentalcare.api.modules.billing.mapper.PaymentPlanMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Installment;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import com.dentalcare.api.modules.billing.model.PaymentPlanStatus;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentPlanServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant GUATEMALA_EVENING = Instant.parse("2026-10-05T02:00:00Z");
    private static final LocalDate GUATEMALA_TODAY = LocalDate.of(2026, 10, 4);

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentPlanRepository paymentPlanRepository;

    private PaymentPlanService paymentPlanService;

    @BeforeEach
    void setUp() {
        paymentPlanService = new PaymentPlanServiceImpl(
                chargeRepository, paymentRepository, paymentPlanRepository, new PaymentPlanMapper(), CLOCK);
    }

    @Test
    void createsInstallmentsThatSumThePendingBalance() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        UUID actorId = UUID.randomUUID();
        stubCharge(patient, charge, "0.00");
        when(paymentPlanRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentPlanResponse response = paymentPlanService.create(
                patient.getId(), charge.getId(), actorId, new CreatePaymentPlanRequest(3, TODAY));

        ArgumentCaptor<PaymentPlan> saved = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(paymentPlanRepository).saveAndFlush(saved.capture());
        PaymentPlan plan = saved.getValue();
        assertThat(plan.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(plan.getBaselinePaid()).isEqualByComparingTo("0.00");
        assertThat(plan.getInstallments()).extracting(Installment::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        assertThat(plan.getInstallments().stream().map(Installment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(plan.getTotalAmount());
        assertThat(plan.getInstallments()).extracting(Installment::getDueDate)
                .containsExactly(TODAY, TODAY.plusMonths(1), TODAY.plusMonths(2));
        assertThat(plan.getCreatedByUserId()).isEqualTo(actorId);
        assertThat(plan.getCreatedAt()).isEqualTo(NOW);
        assertThat(plan.getStatus()).isEqualTo(PaymentPlanStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(PaymentPlanViewStatus.ACTIVE);
        assertThat(response.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(response.pendingAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void priorPartialPaymentSetsTotalToThePendingBalance() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(patient, charge, "40.00");
        when(paymentPlanRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        paymentPlanService.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(2, TODAY));

        ArgumentCaptor<PaymentPlan> saved = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(paymentPlanRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBaselinePaid()).isEqualByComparingTo("40.00");
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(saved.getValue().getInstallments()).extracting(Installment::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("30.00"));
    }

    @Test
    void paidChargeIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(patient, charge, "100.00");

        assertThatThrownBy(() -> paymentPlanService.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(3, TODAY)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge has no pending balance");

        verify(paymentPlanRepository, never()).saveAndFlush(any());
    }

    @Test
    void secondActivePlanIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(storedPlan(patient.getId(), charge.getId(), "100.00", "0.00", TODAY,
                        "33.33", "33.33", "33.34")));

        assertThatThrownBy(() -> paymentPlanService.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(3, TODAY)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An active payment plan already exists for this charge");

        verify(paymentPlanRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentActivePlanConstraintIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(patient, charge, "0.00");
        SQLException sqlException = new SQLException(
                "duplicate key value violates unique constraint \"uq_billing_payment_plans_active_charge\"", "23505");
        when(paymentPlanRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("could not execute statement", sqlException,
                        "uq_billing_payment_plans_active_charge")));

        assertThatThrownBy(() -> paymentPlanService.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(3, TODAY)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An active payment plan already exists for this charge");
    }

    @Test
    void chargeOfAnotherPatientIsNotFound() {
        UUID otherPatientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        when(chargeRepository.findByIdAndPatientIdForUpdate(chargeId, otherPatientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPlanService.create(otherPatientId, chargeId, UUID.randomUUID(),
                new CreatePaymentPlanRequest(3, TODAY)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Charge not found");

        verify(paymentRepository, never()).sumAmountByChargeId(any());
    }

    @Test
    void pastFirstDueDateIsRejected() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");

        assertThatThrownBy(() -> paymentPlanService.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(3, TODAY.minusDays(1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("First due date must be today or later");

        verify(chargeRepository, never()).findByIdAndPatientIdForUpdate(any(), any());
    }

    @Test
    void acceptsGuatemalaTodayWhenUtcDateIsAlreadyTomorrow() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(patient, charge, "0.00");
        when(paymentPlanRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentPlanService service = serviceAt(GUATEMALA_EVENING);

        PaymentPlanResponse response = service.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(2, GUATEMALA_TODAY));

        assertThat(response.firstDueDate()).isEqualTo(GUATEMALA_TODAY);
        assertThat(response.installments()).extracting(installment -> installment.dueDate())
                .containsExactly(GUATEMALA_TODAY, GUATEMALA_TODAY.plusMonths(1));
    }

    @Test
    void rejectsTheDayBeforeGuatemalaTodayWhenUtcDateIsAlreadyTomorrow() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        PaymentPlanService service = serviceAt(GUATEMALA_EVENING);

        assertThatThrownBy(() -> service.create(patient.getId(), charge.getId(), UUID.randomUUID(),
                new CreatePaymentPlanRequest(2, GUATEMALA_TODAY.minusDays(1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("First due date must be today or later");

        verify(chargeRepository, never()).findByIdAndPatientIdForUpdate(any(), any());
    }

    @Test
    void overdueUsesGuatemalaDateWhenUtcDateIsAlreadyTomorrow() {
        Patient patient = patient();
        LocalDate yesterday = LocalDate.of(2026, 10, 3);
        LocalDate utcToday = LocalDate.of(2026, 10, 5);
        PaymentPlan plan = new PaymentPlan(UUID.randomUUID(), UUID.randomUUID(), patient.getId(), 3,
                new BigDecimal("100.00"), new BigDecimal("0.00"), yesterday, UUID.randomUUID(), GUATEMALA_EVENING);
        plan.addInstallment(new Installment(UUID.randomUUID(), 1, new BigDecimal("33.33"), yesterday));
        plan.addInstallment(new Installment(UUID.randomUUID(), 2, new BigDecimal("33.33"), GUATEMALA_TODAY));
        plan.addInstallment(new Installment(UUID.randomUUID(), 3, new BigDecimal("33.34"), utcToday));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(plan.getChargeId(), patient.getId()))
                .thenReturn(Optional.of(plan));
        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(BigDecimal.ZERO);
        PaymentPlanService service = serviceAt(GUATEMALA_EVENING);

        PaymentPlanResponse unpaid = service.findActiveByCharge(patient.getId(), plan.getChargeId());

        assertThat(unpaid.installments().get(0).overdue()).isTrue();
        assertThat(unpaid.installments().get(1).overdue()).isFalse();
        assertThat(unpaid.installments().get(2).overdue()).isFalse();

        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(new BigDecimal("33.33"));
        PaymentPlanResponse firstPaid = service.findActiveByCharge(patient.getId(), plan.getChargeId());
        assertThat(firstPaid.installments().get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstPaid.installments().get(0).overdue()).isFalse();
        assertThat(firstPaid.installments().get(1).overdue()).isFalse();
    }

    @Test
    void derivesInstallmentStatusFromRealPayments() {
        Patient patient = patient();
        PaymentPlan plan = storedPlan(patient.getId(), UUID.randomUUID(), "100.00", "0.00", TODAY,
                "33.33", "33.33", "33.34");
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(plan.getChargeId(), patient.getId()))
                .thenReturn(Optional.of(plan));

        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(new BigDecimal("33.33"));
        PaymentPlanResponse firstPaid = paymentPlanService.findActiveByCharge(patient.getId(), plan.getChargeId());
        assertThat(firstPaid.status()).isEqualTo(PaymentPlanViewStatus.ACTIVE);
        assertThat(firstPaid.installments().get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstPaid.installments().get(0).paidAmount()).isEqualByComparingTo("33.33");
        assertThat(firstPaid.installments().get(1).status()).isEqualTo(InstallmentStatus.PENDING);

        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(new BigDecimal("40.00"));
        PaymentPlanResponse partial = paymentPlanService.findActiveByCharge(patient.getId(), plan.getChargeId());
        assertThat(partial.installments().get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(partial.installments().get(1).status()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(partial.installments().get(1).paidAmount()).isEqualByComparingTo("6.67");
        assertThat(partial.installments().get(2).status()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(partial.paidAmount()).isEqualByComparingTo("40.00");
        assertThat(partial.pendingAmount()).isEqualByComparingTo("60.00");

        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(new BigDecimal("100.00"));
        PaymentPlanResponse completed = paymentPlanService.findActiveByCharge(patient.getId(), plan.getChargeId());
        assertThat(completed.status()).isEqualTo(PaymentPlanViewStatus.COMPLETED);
        assertThat(completed.installments()).allMatch(installment -> installment.status() == InstallmentStatus.PAID);
        assertThat(completed.paidAmount()).isEqualByComparingTo("100.00");
        assertThat(completed.pendingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void unpaidInstallmentBeforeTodayIsOverdue() {
        Patient patient = patient();
        LocalDate firstDueDate = LocalDate.of(2026, 9, 1);
        PaymentPlan plan = storedPlan(patient.getId(), UUID.randomUUID(), "100.00", "0.00", firstDueDate,
                "33.33", "33.33", "33.34");
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(plan.getChargeId(), patient.getId()))
                .thenReturn(Optional.of(plan));
        when(paymentRepository.sumAmountByChargeId(plan.getChargeId())).thenReturn(new BigDecimal("33.33"));

        PaymentPlanResponse response = paymentPlanService.findActiveByCharge(patient.getId(), plan.getChargeId());

        assertThat(response.installments().get(0).overdue()).isFalse();
        assertThat(response.installments().get(1).overdue()).isTrue();
        assertThat(response.installments().get(2).overdue()).isFalse();
    }

    @Test
    void cancelRequiresReasonRejectsASecondCancelAndAllowsANewPlan() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        PaymentPlan plan = storedPlan(patient.getId(), charge.getId(), "100.00", "0.00", TODAY,
                "33.33", "33.33", "33.34");
        UUID actorId = UUID.randomUUID();
        when(paymentPlanRepository.findByIdAndPatientIdForUpdate(plan.getId(), patient.getId()))
                .thenReturn(Optional.of(plan));
        when(paymentRepository.sumAmountByChargeId(charge.getId())).thenReturn(BigDecimal.ZERO);
        when(paymentPlanRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> paymentPlanService.cancel(patient.getId(), plan.getId(), actorId,
                new CancelPaymentPlanRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cancel reason is required");

        PaymentPlanResponse cancelled = paymentPlanService.cancel(patient.getId(), plan.getId(), actorId,
                new CancelPaymentPlanRequest("  Cliente desistió  "));
        assertThat(cancelled.status()).isEqualTo(PaymentPlanViewStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Cliente desistió");
        assertThat(cancelled.cancelledByUserId()).isEqualTo(actorId);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        assertThat(plan.getStatus()).isEqualTo(PaymentPlanStatus.CANCELLED);

        assertThatThrownBy(() -> paymentPlanService.cancel(patient.getId(), plan.getId(), actorId,
                new CancelPaymentPlanRequest("Otra vez")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Payment plan is already cancelled");

        stubCharge(patient, charge, "0.00");
        PaymentPlanResponse replacement = paymentPlanService.create(
                patient.getId(), charge.getId(), actorId, new CreatePaymentPlanRequest(2, TODAY));
        assertThat(replacement.status()).isEqualTo(PaymentPlanViewStatus.ACTIVE);
        assertThat(replacement.totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void planOfAnotherPatientIsNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(paymentPlanRepository.findByIdAndPatientIdForUpdate(planId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPlanService.cancel(patientId, planId, UUID.randomUUID(),
                new CancelPaymentPlanRequest("Cierre")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment plan not found");
    }

    private PaymentPlanService serviceAt(Instant instant) {
        return new PaymentPlanServiceImpl(chargeRepository, paymentRepository, paymentPlanRepository,
                new PaymentPlanMapper(), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void stubCharge(Patient patient, Charge charge, String paid) {
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId()))
                .thenReturn(Optional.empty());
        when(paymentRepository.sumAmountByChargeId(charge.getId())).thenReturn(new BigDecimal(paid));
    }

    private PaymentPlan storedPlan(UUID patientId, UUID chargeId, String total, String baseline, LocalDate firstDueDate,
                                   String... amounts) {
        PaymentPlan plan = new PaymentPlan(UUID.randomUUID(), chargeId, patientId, amounts.length,
                new BigDecimal(total), new BigDecimal(baseline), firstDueDate, UUID.randomUUID(), NOW);
        for (int index = 0; index < amounts.length; index++) {
            plan.addInstallment(new Installment(UUID.randomUUID(), index + 1, new BigDecimal(amounts[index]),
                    firstDueDate.plusMonths(index)));
        }
        return plan;
    }

    private Charge charge(Patient patient, String amount) {
        return new Charge(UUID.randomUUID(), patient, "Limpieza", new BigDecimal(amount), NOW.minusSeconds(60));
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-00001");
        patient.setName("Paciente Prueba");
        patient.setDpi("2987451200101");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-1234");
        patient.setCreatedAt(NOW.minusSeconds(7200));
        patient.setUpdatedAt(NOW.minusSeconds(7200));
        return patient;
    }
}
