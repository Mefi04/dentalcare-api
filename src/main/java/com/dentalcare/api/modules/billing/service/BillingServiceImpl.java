package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.AccountSummaryResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingServiceImpl implements BillingService {

    private static final int MAX_CONCEPT_LENGTH = 200;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final BillingMapper billingMapper;
    private final Clock clock;

    public BillingServiceImpl(ChargeRepository chargeRepository,
                              PaymentRepository paymentRepository,
                              PatientRepository patientRepository,
                              BillingMapper billingMapper,
                              Clock clock) {
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.patientRepository = patientRepository;
        this.billingMapper = billingMapper;
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
        return toChargeResponse(charge, ZERO);
    }

    private AccountStatementResponse buildStatement(UUID patientId) {
        List<Charge> charges = chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId);
        List<Payment> payments = paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patientId);

        Map<UUID, BigDecimal> paidByCharge = new HashMap<>();
        BigDecimal paid = ZERO;
        BigDecimal advances = ZERO;
        for (Payment payment : payments) {
            if (payment.getKind() == PaymentKind.ADVANCE) {
                advances = advances.add(payment.getAmount());
            } else {
                paid = paid.add(payment.getAmount());
                paidByCharge.merge(payment.getCharge().getId(), payment.getAmount(), BigDecimal::add);
            }
        }
        BigDecimal charged = charges.stream().map(Charge::getAmount).reduce(ZERO, BigDecimal::add);
        AccountSummaryResponse summary = new AccountSummaryResponse(
                charged, paid, advances, charged.subtract(paid).subtract(advances));

        return new AccountStatementResponse(
                patientId,
                summary,
                charges.stream()
                        .map(charge -> toChargeResponse(charge, paidByCharge.getOrDefault(charge.getId(), ZERO)))
                        .toList(),
                payments.stream().map(billingMapper::toPaymentResponse).toList());
    }

    private ChargeResponse toChargeResponse(Charge charge, BigDecimal paid) {
        BigDecimal pending = charge.getAmount().subtract(paid);
        return billingMapper.toChargeResponse(charge, paid, pending, statusOf(paid, pending));
    }

    private ChargeStatus statusOf(BigDecimal paid, BigDecimal pending) {
        if (pending.signum() <= 0) {
            return ChargeStatus.PAID;
        }
        return paid.signum() > 0 ? ChargeStatus.PARTIALLY_PAID : ChargeStatus.PENDING;
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
