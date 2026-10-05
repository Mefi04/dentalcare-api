package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.mapper.ReceiptMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.Receipt;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.ReceiptRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
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
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ClinicSettingsService clinicSettingsService;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptServiceImpl(paymentRepository, receiptRepository, refundRepository,
                new ReceiptMapper(), clinicSettingsService, CLOCK);
    }

    @Test
    void issuesReceiptFromTheChargeOfThePayment() {
        Patient patient = patient();
        Charge charge = new Charge(UUID.randomUUID(), patient, "Limpieza", money("120.00"), NOW.minusSeconds(60));
        Payment payment = payment(patient, charge, PaymentKind.PARTIAL_PAYMENT, PaymentMethod.CARD, "40.00");
        UUID actorId = UUID.randomUUID();
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.existsByPaymentId(payment.getId())).thenReturn(false);
        when(receiptRepository.nextReceiptNumber()).thenReturn(4L);
        when(receiptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicSettingsService.get()).thenReturn(clinicSettings(null, null, null, null, null, null));

        ReceiptResponse response = receiptService.issue(patient.getId(), payment.getId(), actorId);

        ArgumentCaptor<Receipt> saved = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).saveAndFlush(saved.capture());
        Receipt receipt = saved.getValue();
        assertThat(receipt.getReceiptNumber()).isEqualTo(4L);
        assertThat(receipt.getPaymentId()).isEqualTo(payment.getId());
        assertThat(receipt.getPatientId()).isEqualTo(patient.getId());
        assertThat(receipt.getConcept()).isEqualTo("Limpieza");
        assertThat(receipt.getAmount()).isEqualByComparingTo("40.00");
        assertThat(receipt.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.ISSUED);
        assertThat(receipt.getIssuedAt()).isEqualTo(NOW);
        assertThat(receipt.getIssuedByUserId()).isEqualTo(actorId);
        assertThat(response.concept()).isEqualTo("Limpieza");
        assertThat(response.receiptNumber()).isEqualTo(4L);
        assertThat(response.issuedByUserId()).isEqualTo(actorId);
        assertThat(response.voidedAt()).isNull();
        assertThat(response.voidReason()).isNull();
        assertThat(response.clinic()).isNotNull();
        assertThat(response.clinic().tradeName()).isNull();
    }

    @Test
    void issuedReceiptIncludesCurrentClinicSettings() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "25.00");
        when(clinicSettingsService.get()).thenReturn(clinicSettings(
                "DentalCare Central", "1234567-8", "5555-1234", "info@example.test", "Zona 1", "Guatemala"));
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId()))
                .thenReturn(Optional.of(payment));
        when(receiptRepository.existsByPaymentId(payment.getId())).thenReturn(false);
        when(receiptRepository.nextReceiptNumber()).thenReturn(5L);
        when(receiptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptResponse response = receiptService.issue(patient.getId(), payment.getId(), UUID.randomUUID());

        assertThat(response.clinic().tradeName()).isEqualTo("DentalCare Central");
        assertThat(response.clinic().nit()).isEqualTo("1234567-8");
        assertThat(response.clinic().phone()).isEqualTo("5555-1234");
        assertThat(response.clinic().email()).isEqualTo("info@example.test");
        assertThat(response.clinic().address()).isEqualTo("Zona 1");
        assertThat(response.clinic().city()).isEqualTo("Guatemala");
    }

    @Test
    void voidedReceiptReturnsPersistedVoidDetails() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "10.00");
        Receipt receipt = new Receipt(UUID.randomUUID(), 9L, payment.getId(), patient.getId(), "Anticipo",
                payment.getAmount(), payment.getMethod(), UUID.randomUUID(), NOW.minusSeconds(60));
        receipt.voidReceipt(NOW, "Duplicado");
        when(paymentRepository.findByIdAndPatientId(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(receipt));

        ReceiptResponse response = receiptService.findByPayment(patient.getId(), payment.getId());

        assertThat(response.status()).isEqualTo(ReceiptStatus.VOID);
        assertThat(response.voidedAt()).isEqualTo(NOW);
        assertThat(response.voidReason()).isEqualTo("Duplicado");
    }

    @Test
    void issuesAdvanceReceiptWithAnticipoConcept() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "25.00");
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.existsByPaymentId(payment.getId())).thenReturn(false);
        when(receiptRepository.nextReceiptNumber()).thenReturn(5L);
        when(receiptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptResponse response = receiptService.issue(patient.getId(), payment.getId(), UUID.randomUUID());

        assertThat(response.concept()).isEqualTo("Anticipo");
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.status()).isEqualTo(ReceiptStatus.ISSUED);
    }

    @Test
    void secondReceiptOfTheSamePaymentIsConflict() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.TRANSFER, "10.00");
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.existsByPaymentId(payment.getId())).thenReturn(true);

        assertThatThrownBy(() -> receiptService.issue(patient.getId(), payment.getId(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A receipt has already been issued for this payment");

        verify(receiptRepository, never()).nextReceiptNumber();
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicatePaymentConstraintIsConflict() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CHECK, "10.00");
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.existsByPaymentId(payment.getId())).thenReturn(false);
        when(receiptRepository.nextReceiptNumber()).thenReturn(6L);
        SQLException sqlException = new SQLException(
                "duplicate key value violates unique constraint \"uq_billing_receipts_payment\"", "23505");
        when(receiptRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("could not execute statement", sqlException, "uq_billing_receipts_payment")));

        assertThatThrownBy(() -> receiptService.issue(patient.getId(), payment.getId(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A receipt has already been issued for this payment");
    }

    @Test
    void paymentOfAnotherPatientIsNotFound() {
        Patient owner = patient();
        UUID otherPatientId = UUID.randomUUID();
        Payment payment = payment(owner, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "10.00");
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), otherPatientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.issue(otherPatientId, payment.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment not found");

        verify(receiptRepository, never()).existsByPaymentId(any());
    }

    @Test
    void missingPaymentIsNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndPatientIdForUpdate(paymentId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.issue(patientId, paymentId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void issueOfFullyRefundedPaymentIsConflict() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "40.00");
        when(paymentRepository.findByIdAndPatientIdForUpdate(payment.getId(), patient.getId()))
                .thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentId(payment.getId())).thenReturn(money("40.00"));

        assertThatThrownBy(() -> receiptService.issue(patient.getId(), payment.getId(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Payment is fully refunded");

        verify(receiptRepository, never()).nextReceiptNumber();
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingReceiptIsNotFound() {
        Patient patient = patient();
        Payment payment = payment(patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH, "10.00");
        when(paymentRepository.findByIdAndPatientId(payment.getId(), patient.getId())).thenReturn(Optional.of(payment));
        when(receiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.findByPayment(patient.getId(), payment.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Receipt not found");
    }

    private Payment payment(Patient patient, Charge charge, PaymentKind kind, PaymentMethod method, String amount) {
        return new Payment(UUID.randomUUID(), patient, charge, kind, method, money(amount), NOW.minusSeconds(30));
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

    private BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private ClinicSettingsResponse clinicSettings(String tradeName, String nit, String phone, String email,
                                                  String address, String city) {
        return new ClinicSettingsResponse((short) 1, tradeName, nit, phone, email, address, city,
                null, null, null, NOW, NOW);
    }
}
