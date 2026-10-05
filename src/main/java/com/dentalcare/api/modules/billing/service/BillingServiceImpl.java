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
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.Charge;
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
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingServiceImpl implements BillingService {

    private static final int MAX_CONCEPT_LENGTH = 200;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeAdjustmentRepository chargeAdjustmentRepository;
    private final RefundRepository refundRepository;
    private final PatientRepository patientRepository;
    private final BillingMapper billingMapper;
    private final ChargeLedger chargeLedger;
    private final CashShiftService cashShiftService;
    private final Clock clock;

    public BillingServiceImpl(ChargeRepository chargeRepository,
                              PaymentRepository paymentRepository,
                              ChargeAdjustmentRepository chargeAdjustmentRepository,
                              RefundRepository refundRepository,
                              PatientRepository patientRepository,
                              BillingMapper billingMapper,
                              ChargeLedger chargeLedger,
                              CashShiftService cashShiftService,
                              Clock clock) {
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.chargeAdjustmentRepository = chargeAdjustmentRepository;
        this.refundRepository = refundRepository;
        this.patientRepository = patientRepository;
        this.billingMapper = billingMapper;
        this.chargeLedger = chargeLedger;
        this.cashShiftService = cashShiftService;
        this.clock = clock;
    }

    // Repeatable read gives both statement queries the same snapshot, so totals always match the listed rows.
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountStatementResponse findAccountStatement(UUID patientId) {
        requirePatientId(patientId);
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        return buildStatement(patientId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountStatementResponse findAccountStatementForAuthenticatedPatient(UUID authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
        Patient patient = patientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        return buildStatement(patient.getId());
    }

    @Override
    @Transactional
    public ChargeResponse createCharge(UUID patientId, CreateChargeRequest request) {
        requirePatientId(patientId);
        if (request == null) {
            throw new BadRequestException("Charge is required");
        }
        String concept = normalizeConcept(request.concept());
        BigDecimal amount = normalizeAmount(request.amount());
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        Charge charge = chargeRepository.saveAndFlush(
                new Charge(UUID.randomUUID(), patient, concept, amount, clock.instant()));
        return toChargeResponse(charge, chargeLedger.position(charge.getAmount(), ZERO, ZERO, ZERO, false));
    }

    @Override
    @Transactional
    public PaymentResponse registerPayment(UUID patientId, CreatePaymentRequest request, UUID actorUserId) {
        requirePatientId(patientId);
        if (request == null) {
            throw new BadRequestException("Payment is required");
        }
        if (request.method() == null) {
            throw new BadRequestException("Payment method is required");
        }
        if (actorUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
        BigDecimal amount = normalizeAmount(request.amount());
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        // Lock the open shift before the charge. Close locks only that shift row and reads cash
        // totals while holding it, so a cash payment is either included in the expected amount
        // or rejected once the shift is CLOSED. Non-cash payments do not take this lock.
        CashShift cashShift = null;
        if (request.method() == PaymentMethod.CASH) {
            cashShift = cashShiftService.requireOpenShiftForUpdate(actorUserId);
        }

        Charge charge = null;
        PaymentKind kind = PaymentKind.ADVANCE;
        if (request.chargeId() != null) {
            // Locking the charge serializes concurrent payments, so their sum can never exceed its amount.
            charge = chargeRepository.findByIdAndPatientIdForUpdate(request.chargeId(), patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
            ChargePosition position = positionOf(charge);
            if (position.voided()) {
                throw new ConflictException("Charge is voided");
            }
            if (position.pending().signum() <= 0) {
                throw new ConflictException("Charge is already paid");
            }
            if (amount.compareTo(position.pending()) > 0) {
                throw new ConflictException("Payment amount exceeds the pending balance of the charge");
            }
            kind = amount.compareTo(position.pending()) == 0 ? PaymentKind.PAYMENT : PaymentKind.PARTIAL_PAYMENT;
        }

        Payment payment = paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(), patient, charge, kind, request.method(), amount, clock.instant(),
                actorUserId, cashShift));
        return billingMapper.toPaymentResponse(payment);
    }

    private AccountStatementResponse buildStatement(UUID patientId) {
        List<Charge> charges = chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId);
        List<Payment> payments = paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId);
        List<ChargeAdjustment> adjustments = chargeAdjustmentRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patientId);
        List<Refund> refunds = refundRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patientId);

        Map<UUID, Payment> paymentsById = new HashMap<>();
        Map<UUID, BigDecimal> grossByCharge = new HashMap<>();
        BigDecimal advanceGross = ZERO;
        for (Payment payment : payments) {
            paymentsById.put(payment.getId(), payment);
            if (payment.getKind() == PaymentKind.ADVANCE) {
                advanceGross = advanceGross.add(payment.getAmount());
            } else {
                grossByCharge.merge(payment.getCharge().getId(), payment.getAmount(), BigDecimal::add);
            }
        }

        Map<UUID, BigDecimal> discountByCharge = new HashMap<>();
        Set<UUID> voidedCharges = new HashSet<>();
        for (ChargeAdjustment adjustment : adjustments) {
            if (adjustment.getType() == ChargeAdjustmentType.VOID) {
                voidedCharges.add(adjustment.getChargeId());
            } else if (adjustment.getAmount() != null) {
                discountByCharge.merge(adjustment.getChargeId(), adjustment.getAmount(), BigDecimal::add);
            }
        }

        Map<UUID, BigDecimal> refundedByCharge = new HashMap<>();
        BigDecimal advanceRefunded = ZERO;
        for (Refund refund : refunds) {
            Payment payment = paymentsById.get(refund.getPaymentId());
            if (payment == null || payment.getKind() == PaymentKind.ADVANCE || payment.getCharge() == null) {
                advanceRefunded = advanceRefunded.add(refund.getAmount());
            } else {
                refundedByCharge.merge(payment.getCharge().getId(), refund.getAmount(), BigDecimal::add);
            }
        }

        BigDecimal charged = ZERO;
        BigDecimal paid = ZERO;
        List<ChargeResponse> chargeResponses = new ArrayList<>();
        for (Charge charge : charges) {
            ChargePosition position = chargeLedger.position(
                    charge.getAmount(),
                    grossByCharge.getOrDefault(charge.getId(), ZERO),
                    discountByCharge.getOrDefault(charge.getId(), ZERO),
                    refundedByCharge.getOrDefault(charge.getId(), ZERO),
                    voidedCharges.contains(charge.getId()));
            if (!position.voided()) {
                charged = charged.add(charge.getAmount().subtract(position.discount()));
            }
            paid = paid.add(position.netPaid());
            chargeResponses.add(toChargeResponse(charge, position));
        }
        BigDecimal advances = advanceGross.subtract(advanceRefunded);
        return new AccountStatementResponse(
                patientId,
                new AccountSummaryResponse(charged, paid, advances, charged.subtract(paid).subtract(advances)),
                chargeResponses,
                payments.stream().map(billingMapper::toPaymentResponse).toList());
    }

    private ChargePosition positionOf(Charge charge) {
        return chargeLedger.position(
                charge.getAmount(),
                paymentRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.sumDiscountByChargeId(charge.getId()),
                refundRepository.sumAmountByChargeId(charge.getId()),
                chargeAdjustmentRepository.existsByChargeIdAndType(charge.getId(), ChargeAdjustmentType.VOID));
    }

    private ChargeResponse toChargeResponse(Charge charge, ChargePosition position) {
        return billingMapper.toChargeResponse(
                charge, position.netPaid(), position.pending(), statusOf(position),
                position.discount(), position.refunded());
    }

    private ChargeStatus statusOf(ChargePosition position) {
        return position.status();
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

    private void requirePatientId(UUID patientId) {
        if (patientId == null) {
            throw new BadRequestException("Patient id is required");
        }
    }
}
