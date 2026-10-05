package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeDiscountRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateRefundRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.VoidChargeRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.ledger.ChargeLedger;
import com.dentalcare.api.modules.billing.ledger.ChargePosition;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.mapper.ChargeAdjustmentMapper;
import com.dentalcare.api.modules.billing.mapper.PaymentPlanMapper;
import com.dentalcare.api.modules.billing.mapper.ReceiptMapper;
import com.dentalcare.api.modules.billing.mapper.RefundMapper;
import com.dentalcare.api.modules.billing.model.CashShiftStatus;
import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.Refund;
import com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.RefundRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChargeAdjustmentServiceImpl.class, RefundServiceImpl.class, BillingServiceImpl.class,
        CashShiftServiceImpl.class, BillingMapper.class, CashShiftMapper.class, ChargeAdjustmentMapper.class,
        RefundMapper.class, PaymentPlanMapper.class, ReceiptMapper.class, ChargeLedger.class,
        BillingAdjustmentServiceIntegrationTests.ClockConfiguration.class})
class BillingAdjustmentServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private BillingService billingService;
    @Autowired
    private CashShiftService cashShiftService;
    @Autowired
    private ChargeAdjustmentService chargeAdjustmentService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private ChargeLedger chargeLedger;
    @Autowired
    private ChargeAdjustmentRepository chargeAdjustmentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentRefundsNeverExceedThePayment() throws Exception {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Limpieza", new BigDecimal("100.00")));
        PaymentResponse payment = billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("100.00"), PaymentMethod.CARD), user.getId());

        List<Throwable> failures = runConcurrent(8, () -> refundService.create(patient.getId(), payment.id(), user.getId(),
                new CreateRefundRequest(new BigDecimal("100.00"), "Total"), null));

        assertThat(failures).allMatch(ConflictException.class::isInstance);
        assertThat(failures).hasSize(7);
        assertThat(refundRepository.sumAmountByPaymentId(payment.id())).isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentIdenticalIdempotencyKeyCreatesOneRefund() throws Exception {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Consulta", new BigDecimal("80.00")));
        PaymentResponse payment = billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("80.00"), PaymentMethod.CARD), user.getId());
        CreateRefundRequest request = new CreateRefundRequest(new BigDecimal("20.00"), "Misma clave");

        List<Object> outcomes = runConcurrentValues(8, () -> refundService.create(
                patient.getId(), payment.id(), user.getId(), request, "same-key"));

        assertThat(outcomes).allMatch(outcome -> outcome instanceof RefundResult || outcome instanceof ConflictException);
        assertThat(outcomes.stream().filter(RefundResult.class::isInstance).count()).isPositive();
        assertThat(refundRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);
        assertThat(outcomes.stream().noneMatch(outcome -> outcome instanceof Exception
                && !(outcome instanceof ConflictException))).isTrue();
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmountIsConflict() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Consulta", new BigDecimal("80.00")));
        PaymentResponse payment = billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("80.00"), PaymentMethod.CARD), user.getId());

        refundService.create(patient.getId(), payment.id(), user.getId(),
                new CreateRefundRequest(new BigDecimal("10.00"), "Primera"), "once");

        assertThatThrownBy(() -> refundService.create(patient.getId(), payment.id(), user.getId(),
                new CreateRefundRequest(new BigDecimal("12.00"), "Primera"), "once"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency key was already used with different data");
        assertThat(refundRepository.findByPatientIdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);
    }

    @Test
    void concurrentCashRefundAndCloseStayConsistentWithExpected() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            User user = createUser();
            Patient patient = createPatient();
            var shift = cashShiftService.open(user.getId(), new OpenCashShiftRequest(new BigDecimal("100.00"), null));
            ChargeResponse charge = billingService.createCharge(patient.getId(),
                    new CreateChargeRequest("Efectivo", new BigDecimal("30.00")));
            PaymentResponse payment = billingService.registerPayment(patient.getId(),
                    new CreatePaymentRequest(charge.id(), new BigDecimal("30.00"), PaymentMethod.CASH), user.getId());

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> refunded = executor.submit(() -> {
                    start.await();
                    try {
                        refundService.create(patient.getId(), payment.id(), user.getId(),
                                new CreateRefundRequest(new BigDecimal("30.00"), "Cierre"), null);
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                });
                Future<Boolean> closed = executor.submit(() -> {
                    start.await();
                    cashShiftService.close(user.getId(), shift.id(),
                            new CloseCashShiftRequest(new BigDecimal("100.00"), null));
                    return true;
                });
                start.countDown();
                boolean refundAccepted = refunded.get(30, TimeUnit.SECONDS);
                assertThat(closed.get(30, TimeUnit.SECONDS)).isTrue();

                BigDecimal expected = jdbcTemplate.queryForObject(
                        "SELECT expected_amount FROM billing_cash_shifts WHERE id = ?", BigDecimal.class, shift.id());
                String status = jdbcTemplate.queryForObject(
                        "SELECT status FROM billing_cash_shifts WHERE id = ?", String.class, shift.id());
                BigDecimal cash = paymentRepository.sumCashAmountByCashShiftId(shift.id());
                BigDecimal refunds = refundRepository.sumAmountByCashShiftId(shift.id());
                assertThat(status).isEqualTo(CashShiftStatus.CLOSED.name());
                assertThat(expected).isEqualByComparingTo(new BigDecimal("100.00").add(cash).subtract(refunds));
                if (refundAccepted) {
                    assertThat(refunds).isEqualByComparingTo("30.00");
                } else {
                    assertThat(refunds).isEqualByComparingTo("0");
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void voidUniqueIndexAllowsOneVoid() throws Exception {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Error", new BigDecimal("40.00")));

        List<Throwable> failures = runConcurrent(8, () -> chargeAdjustmentService.voidCharge(
                patient.getId(), charge.id(), user.getId(), new VoidChargeRequest("Duplicado")));

        assertThat(failures).allMatch(ConflictException.class::isInstance);
        assertThat(failures).hasSize(7);
        Integer voids = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM billing_charge_adjustments WHERE charge_id = ? AND type = 'VOID'
                """, Integer.class, charge.id());
        assertThat(voids).isEqualTo(1);
    }

    @Test
    void compositeForeignKeysRejectAMismatchedPatient() {
        User user = createUser();
        Patient owner = createPatient();
        Patient other = createPatient();
        ChargeResponse charge = billingService.createCharge(owner.getId(),
                new CreateChargeRequest("Ajeno", new BigDecimal("25.00")));
        PaymentResponse payment = billingService.registerPayment(owner.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("10.00"), PaymentMethod.CARD), user.getId());

        assertThatThrownBy(() -> chargeAdjustmentRepository.saveAndFlush(new ChargeAdjustment(
                UUID.randomUUID(), charge.id(), other.getId(), ChargeAdjustmentType.DISCOUNT,
                new BigDecimal("1.00"), "Ajeno", user.getId(), user.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> refundRepository.saveAndFlush(new Refund(
                UUID.randomUUID(), payment.id(), other.getId(), new BigDecimal("1.00"), "Ajeno",
                user.getId(), user.getId(), null, null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checksRejectInvalidDiscountAndVoidAmounts() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Chequeo", new BigDecimal("25.00")));

        assertThatThrownBy(() -> chargeAdjustmentRepository.saveAndFlush(new ChargeAdjustment(
                UUID.randomUUID(), charge.id(), patient.getId(), ChargeAdjustmentType.DISCOUNT,
                null, "Sin monto", user.getId(), user.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> chargeAdjustmentRepository.saveAndFlush(new ChargeAdjustment(
                UUID.randomUUID(), charge.id(), patient.getId(), ChargeAdjustmentType.DISCOUNT,
                BigDecimal.ZERO, "Cero", user.getId(), user.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> chargeAdjustmentRepository.saveAndFlush(new ChargeAdjustment(
                UUID.randomUUID(), charge.id(), patient.getId(), ChargeAdjustmentType.VOID,
                new BigDecimal("1.00"), "Con monto", user.getId(), user.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void adjustmentAndRefundAuthoritiesBelongOnlyToAdministrator() {
        List<String> rows = jdbcTemplate.query("""
                SELECT p.code || ':' || r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code IN ('BILLING_ADJUSTMENT_CREATE', 'BILLING_REFUND_CREATE')
                ORDER BY p.code, r.code
                """, (rs, row) -> rs.getString(1));

        assertThat(rows).containsExactly("BILLING_ADJUSTMENT_CREATE:ADMINISTRATOR", "BILLING_REFUND_CREATE:ADMINISTRATOR");
    }

    @Test
    void statementMatchesChargeLedger() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Limpieza", new BigDecimal("100.00")));
        billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("40.00"), PaymentMethod.CARD), user.getId());
        chargeAdjustmentService.discount(patient.getId(), charge.id(), user.getId(),
                new CreateChargeDiscountRequest(new BigDecimal("10.00"), "Cortesia"));
        PaymentResponse payment = billingService.findAccountStatement(patient.getId()).payments().getFirst();
        refundService.create(patient.getId(), payment.id(), user.getId(),
                new CreateRefundRequest(new BigDecimal("15.00"), "Devolucion"), null);

        AccountStatementResponse statement = billingService.findAccountStatement(patient.getId());
        ChargePosition position = chargeLedger.position(
                new BigDecimal("100.00"),
                paymentRepository.sumAmountByChargeId(charge.id()),
                chargeAdjustmentRepository.sumDiscountByChargeId(charge.id()),
                refundRepository.sumAmountByChargeId(charge.id()),
                chargeAdjustmentRepository.existsByChargeIdAndType(charge.id(), ChargeAdjustmentType.VOID));
        ChargeResponse row = statement.charges().getFirst();

        assertThat(row.paid()).isEqualByComparingTo(position.netPaid());
        assertThat(row.discount()).isEqualByComparingTo(position.discount());
        assertThat(row.refunded()).isEqualByComparingTo(position.refunded());
        assertThat(row.pending()).isEqualByComparingTo(position.pending());
        assertThat(row.status()).isEqualTo(position.status());
        assertThat(statement.summary().charged()).isEqualByComparingTo("90.00");
        assertThat(statement.summary().paid()).isEqualByComparingTo(position.netPaid());
        assertThat(statement.summary().balance())
                .isEqualByComparingTo(statement.summary().charged().subtract(statement.summary().paid())
                        .subtract(statement.summary().advances()));
    }

    private List<Throwable> runConcurrent(int threads, Runnable action) throws Exception {
        List<Object> outcomes = runConcurrentValues(threads, () -> {
            action.run();
            return null;
        });
        List<Throwable> failures = new ArrayList<>();
        for (Object outcome : outcomes) {
            if (outcome instanceof Throwable throwable) {
                failures.add(throwable);
            }
        }
        return failures;
    }

    private List<Object> runConcurrentValues(int threads, Callable<Object> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return action.call();
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private User createUser() {
        int n = SEQUENCE.incrementAndGet();
        return userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "adj-user-" + n, "Ajuste " + n,
                "adj-user-" + n + "@example.test", String.format("%013d", n), "hash",
                UserStatus.ACTIVE, NOW, NOW));
    }

    private Patient createPatient() {
        int n = SEQUENCE.incrementAndGet();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("ADJ-" + n);
        patient.setName("Paciente Ajuste");
        patient.setDpi(String.format("7%012d", n));
        patient.setBirthDate(LocalDate.of(1992, 3, 3));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-2222");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patientRepository.saveAndFlush(patient);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
