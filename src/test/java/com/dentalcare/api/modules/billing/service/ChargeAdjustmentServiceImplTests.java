package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeDiscountRequest;
import com.dentalcare.api.modules.billing.dto.request.VoidChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.ChargeAdjustmentResponse;
import com.dentalcare.api.modules.billing.ledger.ChargeLedger;
import com.dentalcare.api.modules.billing.mapper.ChargeAdjustmentMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
class ChargeAdjustmentServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Mock
    private ChargeRepository chargeRepository;
    @Mock
    private ChargeAdjustmentRepository chargeAdjustmentRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentPlanRepository paymentPlanRepository;

    private ChargeAdjustmentService service;
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChargeAdjustmentServiceImpl(chargeRepository, chargeAdjustmentRepository, paymentRepository,
                refundRepository, paymentPlanRepository, new ChargeLedger(), new ChargeAdjustmentMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void discountReducesTheRealPendingBalance() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(charge, patient, "20.00", "0.00", false);
        when(chargeAdjustmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeAdjustmentResponse response = service.discount(patient.getId(), charge.getId(), actorId,
                new CreateChargeDiscountRequest(new BigDecimal("30.00"), "  Cortesia  "));

        ArgumentCaptor<ChargeAdjustment> saved = ArgumentCaptor.forClass(ChargeAdjustment.class);
        verify(chargeAdjustmentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ChargeAdjustmentType.DISCOUNT);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getValue().getReason()).isEqualTo("Cortesia");
        assertThat(saved.getValue().getRequestedByUserId()).isEqualTo(actorId);
        assertThat(saved.getValue().getAuthorizedByUserId()).isEqualTo(actorId);
        assertThat(response.chargeId()).isEqualTo(charge.getId());
    }

    @Test
    void discountAbovePendingIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(charge, patient, "20.00", "0.00", false);

        assertThatThrownBy(() -> service.discount(patient.getId(), charge.getId(), actorId,
                new CreateChargeDiscountRequest(new BigDecimal("80.01"), "Mucho")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Discount exceeds the pending balance");

        verify(chargeAdjustmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void discountOnPaidChargeIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(charge, patient, "100.00", "0.00", false);

        assertThatThrownBy(() -> service.discount(patient.getId(), charge.getId(), actorId,
                new CreateChargeDiscountRequest(new BigDecimal("1.00"), "Tarde")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge has no pending balance");
    }

    @Test
    void discountOnVoidedChargeIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        stubCharge(charge, patient, "0.00", "0.00", true);

        assertThatThrownBy(() -> service.discount(patient.getId(), charge.getId(), actorId,
                new CreateChargeDiscountRequest(new BigDecimal("1.00"), "Tarde")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge is voided");
    }

    @Test
    void discountOnActivePlanIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "100.00");
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(PaymentPlan.class)));

        assertThatThrownBy(() -> service.discount(patient.getId(), charge.getId(), actorId,
                new CreateChargeDiscountRequest(new BigDecimal("1.00"), "Convenio")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cancel the payment plan first");

        verify(chargeAdjustmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void voidSucceedsWhenNothingRemainsPaid() {
        Patient patient = patient();
        Charge charge = charge(patient, "80.00");
        stubCharge(charge, patient, "0.00", "0.00", false);
        when(chargeAdjustmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeAdjustmentResponse response = service.voidCharge(patient.getId(), charge.getId(), actorId,
                new VoidChargeRequest("Error de captura"));

        assertThat(response.type()).isEqualTo(ChargeAdjustmentType.VOID);
        assertThat(response.amount()).isNull();
        assertThat(response.requestedByUserId()).isEqualTo(actorId);
        assertThat(response.authorizedByUserId()).isEqualTo(actorId);
    }

    @Test
    void voidWithNetPaidIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "80.00");
        stubCharge(charge, patient, "25.00", "0.00", false);

        assertThatThrownBy(() -> service.voidCharge(patient.getId(), charge.getId(), actorId,
                new VoidChargeRequest("Con pago")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge has a remaining paid balance");
    }

    @Test
    void voidAfterFullRefundIsAllowed() {
        Patient patient = patient();
        Charge charge = charge(patient, "80.00");
        stubCharge(charge, patient, "80.00", "80.00", false);
        when(chargeAdjustmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeAdjustmentResponse response = service.voidCharge(patient.getId(), charge.getId(), actorId,
                new VoidChargeRequest("Devuelto"));

        assertThat(response.type()).isEqualTo(ChargeAdjustmentType.VOID);
    }

    @Test
    void secondVoidIsConflict() {
        Patient patient = patient();
        Charge charge = charge(patient, "80.00");
        stubCharge(charge, patient, "0.00", "0.00", true);

        assertThatThrownBy(() -> service.voidCharge(patient.getId(), charge.getId(), actorId,
                new VoidChargeRequest("Otra vez")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge is already voided");
    }

    @Test
    void chargeOfAnotherPatientIsNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        when(chargeRepository.findByIdAndPatientIdForUpdate(chargeId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discount(patientId, chargeId, actorId,
                new CreateChargeDiscountRequest(new BigDecimal("1.00"), "Ajeno")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Charge not found");
    }

    private void stubCharge(Charge charge, Patient patient, String grossPaid, String refunded, boolean voided) {
        when(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), patient.getId()))
                .thenReturn(Optional.of(charge));
        when(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId()))
                .thenReturn(Optional.empty());
        when(paymentRepository.sumAmountByChargeId(charge.getId())).thenReturn(new BigDecimal(grossPaid));
        when(refundRepository.sumAmountByChargeId(charge.getId())).thenReturn(new BigDecimal(refunded));
        when(chargeAdjustmentRepository.sumDiscountByChargeId(charge.getId())).thenReturn(BigDecimal.ZERO);
        when(chargeAdjustmentRepository.existsByChargeIdAndType(charge.getId(), ChargeAdjustmentType.VOID))
                .thenReturn(voided);
    }

    private Charge charge(Patient patient, String amount) {
        return new Charge(UUID.randomUUID(), patient, "Limpieza", new BigDecimal(amount), NOW);
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        return patient;
    }
}
