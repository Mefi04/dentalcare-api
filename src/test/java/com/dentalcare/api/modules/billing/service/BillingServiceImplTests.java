package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.AccountSummaryResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.ledger.ChargeLedger;
import com.dentalcare.api.modules.billing.ledger.ChargePosition;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.Refund;
import com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ChargeAdjustmentRepository chargeAdjustmentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private CashShiftService cashShiftService;

    private final UUID actorId = UUID.randomUUID();

    private BillingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillingServiceImpl(chargeRepository, paymentRepository, chargeAdjustmentRepository,
                refundRepository, patientRepository, new BillingMapper(), new ChargeLedger(), cashShiftService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(chargeAdjustmentRepository.sumDiscountByChargeId(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(refundRepository.sumAmountByChargeId(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(chargeAdjustmentRepository.existsByChargeIdAndType(any(), any())).thenReturn(false);
    }

    @Test
    void createsChargeWithTrimmedConceptAndTwoDecimalAmount() {
        Patient patient = patient();
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(chargeRepository.saveAndFlush(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeResponse response = service.createCharge(patient.getId(),
                new CreateChargeRequest("  Limpieza dental  ", money("250")));

        ArgumentCaptor<Charge> saved = ArgumentCaptor.forClass(Charge.class);
        verify(chargeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPatient()).isSameAs(patient);
        assertThat(saved.getValue().getConcept()).isEqualTo("Limpieza dental");
        assertThat(saved.getValue().getAmount()).isEqualTo(money("250.00"));
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(response.id()).isEqualTo(saved.getValue().getId());
        assertThat(response.amount()).isEqualTo(money("250.00"));
        assertThat(response.paid()).isEqualTo(money("0.00"));
        assertThat(response.pending()).isEqualTo(money("250.00"));
        assertThat(response.status()).isEqualTo(ChargeStatus.PENDING);
    }

    @Test
    void createChargeRejectsUnknownPatientWithoutSaving() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCharge(patientId,
                new CreateChargeRequest("Consulta", money("100.00"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");

        verify(chargeRepository, never()).saveAndFlush(any());
    }

    @Test
    void createChargeRejectsInvalidAmountsAndConceptsBeforeTouchingPersistence() {
        UUID patientId = UUID.randomUUID();

        assertChargeRejected(patientId, "Consulta", null, "Amount is required");
        assertChargeRejected(patientId, "Consulta", money("0"), "Amount must be greater than zero");
        assertChargeRejected(patientId, "Consulta", money("-10.00"), "Amount must be greater than zero");
        assertChargeRejected(patientId, "Consulta", money("10.005"), "Amount must not have more than 2 decimals");
        assertChargeRejected(patientId, "Consulta", money("10000000000.00"), "Amount must not exceed 9999999999.99");
        assertChargeRejected(patientId, "   ", money("10.00"), "Concept is required");
        assertChargeRejected(patientId, "x".repeat(201), money("10.00"), "Concept must not exceed 200 characters");
        assertThatThrownBy(() -> service.createCharge(patientId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Charge is required");

        verifyNoInteractions(patientRepository, chargeRepository);
    }

    @Test
    void statementOfPatientWithoutMovementsHasZeroTotals() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId)).thenReturn(List.of());
        when(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId)).thenReturn(List.of());

        AccountStatementResponse response = service.findAccountStatement(patientId);

        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.charges()).isEmpty();
        assertThat(response.payments()).isEmpty();
        assertThat(response.summary()).isEqualTo(new AccountSummaryResponse(
                money("0.00"), money("0.00"), money("0.00"), money("0.00")));
    }

    @Test
    void statementDerivesChargeProgressAndBalanceFromPersistedMovements() {
        Patient patient = patient();
        Charge consultation = charge(patient, "Consulta", "150.00");
        Charge cleaning = charge(patient, "Limpieza", "300.00");
        Charge extraction = charge(patient, "Extracción", "500.00");
        when(patientRepository.existsById(patient.getId())).thenReturn(true);
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId()))
                .thenReturn(List.of(consultation, cleaning, extraction));
        when(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).thenReturn(List.of(
                payment(patient, consultation, PaymentKind.PAYMENT, "150.00"),
                payment(patient, cleaning, PaymentKind.PARTIAL_PAYMENT, "100.00"),
                payment(patient, cleaning, PaymentKind.PARTIAL_PAYMENT, "50.50"),
                payment(patient, null, PaymentKind.ADVANCE, "200.00")));

        AccountStatementResponse response = service.findAccountStatement(patient.getId());

        assertThat(response.summary()).isEqualTo(new AccountSummaryResponse(
                money("950.00"), money("300.50"), money("200.00"), money("449.50")));
        assertThat(response.charges()).extracting(ChargeResponse::id)
                .containsExactly(consultation.getId(), cleaning.getId(), extraction.getId());
        assertThat(response.charges()).extracting(ChargeResponse::paid)
                .containsExactly(money("150.00"), money("150.50"), money("0.00"));
        assertThat(response.charges()).extracting(ChargeResponse::pending)
                .containsExactly(money("0.00"), money("149.50"), money("500.00"));
        assertThat(response.charges()).extracting(ChargeResponse::status)
                .containsExactly(ChargeStatus.PAID, ChargeStatus.PARTIALLY_PAID, ChargeStatus.PENDING);
        assertThat(response.payments()).extracting(PaymentResponse::chargeId)
                .containsExactly(consultation.getId(), cleaning.getId(), cleaning.getId(), null);
        assertThat(response.payments()).extracting(PaymentResponse::kind).containsExactly(
                PaymentKind.PAYMENT, PaymentKind.PARTIAL_PAYMENT, PaymentKind.PARTIAL_PAYMENT, PaymentKind.ADVANCE);
    }

    @Test
    void advancesReduceBalanceWithoutBeingAppliedToCharges() {
        Patient patient = patient();
        Charge consultation = charge(patient, "Consulta", "100.00");
        when(patientRepository.existsById(patient.getId())).thenReturn(true);
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId()))
                .thenReturn(List.of(consultation));
        when(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId()))
                .thenReturn(List.of(payment(patient, null, PaymentKind.ADVANCE, "250.00")));

        AccountStatementResponse response = service.findAccountStatement(patient.getId());

        assertThat(response.summary()).isEqualTo(new AccountSummaryResponse(
                money("100.00"), money("0.00"), money("250.00"), money("-150.00")));
        assertThat(response.charges().get(0).status()).isEqualTo(ChargeStatus.PENDING);
        assertThat(response.charges().get(0).pending()).isEqualTo(money("100.00"));
    }

    @Test
    void statementRejectsUnknownPatient() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> service.findAccountStatement(patientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");

        verifyNoInteractions(chargeRepository, paymentRepository);
    }

    @Test
    void selfServiceResolvesStatementOnlyFromAuthenticatedUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Patient patientA = patient();
        Patient patientB = patient();
        when(patientRepository.findByUser_Id(userA)).thenReturn(Optional.of(patientA));
        when(patientRepository.findByUser_Id(userB)).thenReturn(Optional.of(patientB));
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientA.getId()))
                .thenReturn(List.of(charge(patientA, "Consulta", "100.00")));
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientB.getId()))
                .thenReturn(List.of(charge(patientB, "Corona", "900.00")));
        when(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

        AccountStatementResponse responseA = service.findAccountStatementForAuthenticatedPatient(userA);
        AccountStatementResponse responseB = service.findAccountStatementForAuthenticatedPatient(userB);

        assertThat(responseA.patientId()).isEqualTo(patientA.getId());
        assertThat(responseA.charges()).extracting(ChargeResponse::concept).containsExactly("Consulta");
        assertThat(responseB.patientId()).isEqualTo(patientB.getId());
        assertThat(responseB.charges()).extracting(ChargeResponse::concept).containsExactly("Corona");
        verify(patientRepository, never()).existsById(any());
    }

    @Test
    void selfServiceRejectsUserWithoutLinkedPatient() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAccountStatementForAuthenticatedPatient(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");

        verifyNoInteractions(chargeRepository, paymentRepository);
    }

    @Test
    void registersAdvanceWithoutChargeAndWithoutLocking() {
        Patient patient = patient();
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.registerPayment(patient.getId(),
                new CreatePaymentRequest(null, money("200"), PaymentMethod.TRANSFER), actorId);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(response.kind()).isEqualTo(PaymentKind.ADVANCE);
        assertThat(response.chargeId()).isNull();
        assertThat(response.amount()).isEqualTo(money("200.00"));
        assertThat(response.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getRegisteredByUserId()).isEqualTo(actorId);
        assertThat(saved.getValue().getCashShift()).isNull();
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
        verifyNoInteractions(chargeRepository);
    }

    @Test
    void amountBelowPendingBalanceIsRegisteredAsPartialPaymentOfLockedCharge() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "300.00");
        stubLockedCharge(patient, charge, "100.00");
        CashShift shift = stubOpenShift();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("50.00"), PaymentMethod.CASH), actorId);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPatient()).isSameAs(patient);
        assertThat(saved.getValue().getCharge()).isSameAs(charge);
        assertThat(saved.getValue().getCashShift()).isSameAs(shift);
        assertThat(saved.getValue().getRegisteredByUserId()).isEqualTo(actorId);
        assertThat(response.kind()).isEqualTo(PaymentKind.PARTIAL_PAYMENT);
        assertThat(response.chargeId()).isEqualTo(charge.getId());
        assertThat(response.amount()).isEqualTo(money("50.00"));
    }

    @Test
    void amountSettlingThePendingBalanceIsRegisteredAsPayment() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "300.00");
        stubLockedCharge(patient, charge, "100.00");
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("200.00"), PaymentMethod.CARD), actorId);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(response.kind()).isEqualTo(PaymentKind.PAYMENT);
        assertThat(saved.getValue().getRegisteredByUserId()).isEqualTo(actorId);
        assertThat(saved.getValue().getCashShift()).isNull();
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
    }

    @Test
    void rejectsPaymentExceedingPendingBalance() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "300.00");
        stubLockedCharge(patient, charge, "100.00");
        stubOpenShift();

        assertThatThrownBy(() -> service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("200.01"), PaymentMethod.CASH), actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Payment amount exceeds the pending balance of the charge");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPaymentOnFullyPaidCharge() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "300.00");
        stubLockedCharge(patient, charge, "300.00");
        stubOpenShift();

        assertThatThrownBy(() -> service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("0.01"), PaymentMethod.CASH), actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge is already paid");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerPaymentOnVoidedChargeIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "100.00");
        stubLockedCharge(patient, charge, "0.00");
        when(chargeAdjustmentRepository.existsByChargeIdAndType(charge.getId(), ChargeAdjustmentType.VOID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("10.00"), PaymentMethod.CARD), actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge is voided");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerPaymentUsesNetPendingAfterDiscount() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "100.00");
        stubLockedCharge(patient, charge, "40.00");
        when(chargeAdjustmentRepository.sumDiscountByChargeId(charge.getId())).thenReturn(money("10.00"));
        when(refundRepository.sumAmountByChargeId(charge.getId())).thenReturn(money("15.00"));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("65.00"), PaymentMethod.CARD), actorId);

        assertThat(response.kind()).isEqualTo(PaymentKind.PAYMENT);
        assertThatThrownBy(() -> service.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.getId(), money("65.01"), PaymentMethod.CARD), actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Payment amount exceeds the pending balance of the charge");
    }

    @Test
    void statementMatchesChargeLedger() {
        Patient patient = patient();
        Charge charge = charge(patient, "Limpieza", "100.00");
        Charge voided = charge(patient, "Anulado", "50.00");
        Payment partial = payment(patient, charge, PaymentKind.PARTIAL_PAYMENT, "40.00");
        Payment advance = payment(patient, null, PaymentKind.ADVANCE, "20.00");
        when(patientRepository.existsById(patient.getId())).thenReturn(true);
        when(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId()))
                .thenReturn(List.of(charge, voided));
        when(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId()))
                .thenReturn(List.of(partial, advance));
        when(chargeAdjustmentRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patient.getId())).thenReturn(List.of(
                new ChargeAdjustment(UUID.randomUUID(), charge.getId(), patient.getId(), ChargeAdjustmentType.DISCOUNT,
                        money("10.00"), "Cortesia", actorId, actorId, NOW),
                new ChargeAdjustment(UUID.randomUUID(), voided.getId(), patient.getId(), ChargeAdjustmentType.VOID,
                        null, "Error de cargo", actorId, actorId, NOW)));
        when(refundRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patient.getId())).thenReturn(List.of(
                new Refund(UUID.randomUUID(), partial.getId(), patient.getId(), money("15.00"), "Devolucion",
                        actorId, actorId, null, null, NOW),
                new Refund(UUID.randomUUID(), advance.getId(), patient.getId(), money("5.00"), "Anticipo",
                        actorId, actorId, null, null, NOW)));

        AccountStatementResponse response = service.findAccountStatement(patient.getId());
        ChargePosition position = new ChargeLedger().position(
                money("100.00"), money("40.00"), money("10.00"), money("15.00"), false);
        ChargeResponse row = response.charges().getFirst();

        assertThat(row.paid()).isEqualByComparingTo(position.netPaid());
        assertThat(row.discount()).isEqualByComparingTo(position.discount());
        assertThat(row.refunded()).isEqualByComparingTo(position.refunded());
        assertThat(row.pending()).isEqualByComparingTo(position.pending());
        assertThat(row.status()).isEqualTo(position.status());
        assertThat(row.pending()).isEqualByComparingTo(row.amount().subtract(row.discount()).subtract(row.paid()));
        assertThat(response.charges().get(1).status()).isEqualTo(ChargeStatus.VOIDED);
        assertThat(response.charges().get(1).pending()).isEqualByComparingTo("0.00");
        assertThat(response.summary()).isEqualTo(new AccountSummaryResponse(
                money("90.00"), position.netPaid(), money("15.00"),
                money("90.00").subtract(position.netPaid()).subtract(money("15.00"))));
    }

    @Test
    void chargeOfAnotherPatientIsReportedAsNotFound() {
        Patient patient = patient();
        UUID foreignChargeId = UUID.randomUUID();
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(chargeRepository.findByIdAndPatientIdForUpdate(foreignChargeId, patient.getId()))
                .thenReturn(Optional.empty());
        stubOpenShift();

        assertThatThrownBy(() -> service.registerPayment(patient.getId(),
                new CreatePaymentRequest(foreignChargeId, money("10.00"), PaymentMethod.CASH), actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Charge not found");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerPaymentRejectsUnknownPatient() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerPayment(patientId,
                new CreatePaymentRequest(UUID.randomUUID(), money("10.00"), PaymentMethod.CASH), actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");

        verifyNoInteractions(chargeRepository, paymentRepository);
        verify(cashShiftService, never()).requireOpenShiftForUpdate(any());
    }

    @Test
    void registerPaymentRejectsInvalidRequestsBeforeTouchingPersistence() {
        UUID patientId = UUID.randomUUID();

        assertPaymentRejected(patientId, null, "Payment is required");
        assertPaymentRejected(patientId, new CreatePaymentRequest(null, money("10.00"), null),
                "Payment method is required");
        assertPaymentRejected(patientId, new CreatePaymentRequest(null, null, PaymentMethod.CASH),
                "Amount is required");
        assertPaymentRejected(patientId, new CreatePaymentRequest(null, money("0.00"), PaymentMethod.CASH),
                "Amount must be greater than zero");
        assertPaymentRejected(patientId, new CreatePaymentRequest(null, money("10.001"), PaymentMethod.CASH),
                "Amount must not have more than 2 decimals");

        verifyNoInteractions(patientRepository, chargeRepository, paymentRepository, cashShiftService);
    }

    private CashShift stubOpenShift() {
        CashShift shift = new CashShift(UUID.randomUUID(), actorId, money("0.00"), null, NOW);
        when(cashShiftService.requireOpenShiftForUpdate(actorId)).thenReturn(shift);
        return shift;
    }

    private void stubLockedCharge(Patient patient, Charge charge, String alreadyPaid) {
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentRepository.sumAmountByChargeId(charge.getId())).thenReturn(money(alreadyPaid));
    }

    private void assertPaymentRejected(UUID patientId, CreatePaymentRequest request, String message) {
        assertThatThrownBy(() -> service.registerPayment(patientId, request, actorId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    private void assertChargeRejected(UUID patientId, String concept, BigDecimal amount, String message) {
        assertThatThrownBy(() -> service.createCharge(patientId, new CreateChargeRequest(concept, amount)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
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

    private Charge charge(Patient patient, String concept, String amount) {
        return new Charge(UUID.randomUUID(), patient, concept, money(amount), NOW.minusSeconds(3600));
    }

    private Payment payment(Patient patient, Charge charge, PaymentKind kind, String amount) {
        return new Payment(UUID.randomUUID(), patient, charge, kind, PaymentMethod.CASH, money(amount), NOW);
    }

    private static BigDecimal money(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
