package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.model.CashMovementType;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.CashShiftStatus;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.repository.CashMovementRepository;
import com.dentalcare.api.modules.billing.repository.CashShiftRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@Import({CashShiftServiceImpl.class, BillingServiceImpl.class, CashShiftMapper.class, BillingMapper.class,
        CashShiftServiceIntegrationTests.ClockConfiguration.class})
class CashShiftServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CashShiftService cashShiftService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private CashShiftRepository cashShiftRepository;

    @Autowired
    private CashMovementRepository cashMovementRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final AtomicInteger sequence = new AtomicInteger();

    @Test
    void partialUniqueIndexAllowsExactlyOneConcurrentOpenShift() throws Exception {
        User user = createUser();
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        long accepted = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        cashShiftService.open(user.getId(), new OpenCashShiftRequest(new BigDecimal("25.00"), null));
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(accepted).isEqualTo(1);
        assertThat(cashShiftRepository.findOpenByUserId(user.getId())).isPresent();
        assertThat(cashShiftRepository.findByUserId(user.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void cashPaymentIsLinkedAndIncludedInExpectedWithoutADuplicatePayment() {
        User user = createUser();
        Patient patient = createPatient();
        CashShiftResponse shift = cashShiftService.open(user.getId(),
                new OpenCashShiftRequest(new BigDecimal("100.00"), "inicio"));
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Limpieza", new BigDecimal("200.00")));

        PaymentResponse payment = billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("25.00"), PaymentMethod.CASH), user.getId());
        cashShiftService.addMovement(user.getId(), shift.id(),
                new CreateCashMovementRequest(CashMovementType.INCOME, new BigDecimal("10.00"), "Fondo"));
        cashShiftService.addMovement(user.getId(), shift.id(),
                new CreateCashMovementRequest(CashMovementType.EXPENSE, new BigDecimal("3.00"), "Insumos"));

        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);
        assertThat(cashShiftIdOf(payment.id())).isEqualTo(shift.id());
        assertThat(paymentRepository.findById(payment.id()).orElseThrow().getRegisteredByUserId()).isEqualTo(user.getId());
        assertThat(cashMovementRepository.findByCashShift_Id(shift.id(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(2);

        CashShiftResponse current = cashShiftService.getCurrent(user.getId());
        assertThat(current.expectedAmount()).isEqualByComparingTo("132.00");

        CashShiftResponse closed = cashShiftService.close(user.getId(), shift.id(),
                new CloseCashShiftRequest(new BigDecimal("130.00"), "cierre"));
        assertThat(closed.status()).isEqualTo(CashShiftStatus.CLOSED);
        assertThat(closed.expectedAmount()).isEqualByComparingTo("132.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("130.00");
        assertThat(closed.difference()).isEqualByComparingTo("-2.00");
        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);

        assertThatThrownBy(() -> billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("10.00"), PaymentMethod.CASH), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No open cash shift");
        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);
    }

    @Test
    void cashPaymentWithoutOpenShiftIsRejectedAndNotPersisted() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Consulta", new BigDecimal("80.00")));

        assertThatThrownBy(() -> billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("10.00"), PaymentMethod.CASH), user.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No open cash shift");

        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).isEmpty();
    }

    @Test
    void nonCashPaymentWithoutOpenShiftIsPersisted() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Consulta", new BigDecimal("80.00")));

        PaymentResponse payment = billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("80.00"), PaymentMethod.CARD), user.getId());

        Payment stored = paymentRepository.findById(payment.id()).orElseThrow();
        assertThat(stored.getRegisteredByUserId()).isEqualTo(user.getId());
        assertThat(stored.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(cashShiftIdOf(payment.id())).isNull();
        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).hasSize(1);
    }

    @Test
    void concurrentCashPaymentAndCloseKeepThePaymentInsideExpected() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            User user = createUser();
            Patient patient = createPatient();
            CashShiftResponse shift = cashShiftService.open(user.getId(),
                    new OpenCashShiftRequest(new BigDecimal("100.00"), null));
            ChargeResponse charge = billingService.createCharge(patient.getId(),
                    new CreateChargeRequest("Ortodoncia", new BigDecimal("80.00")));
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> paid = executor.submit(() -> {
                    start.await();
                    try {
                        billingService.registerPayment(patient.getId(),
                                new CreatePaymentRequest(charge.id(), new BigDecimal("30.00"), PaymentMethod.CASH),
                                user.getId());
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
                boolean paymentAccepted = paid.get(30, TimeUnit.SECONDS);
                assertThat(closed.get(30, TimeUnit.SECONDS)).isTrue();

                CashShift reloaded = cashShiftRepository.findById(shift.id()).orElseThrow();
                BigDecimal cash = paymentRepository.sumCashAmountByCashShiftId(shift.id());
                assertThat(reloaded.getStatus()).isEqualTo(CashShiftStatus.CLOSED);
                assertThat(reloaded.getExpectedAmount()).isEqualByComparingTo(reloaded.getOpeningAmount().add(cash));
                List<Payment> payments = paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId());
                if (paymentAccepted) {
                    assertThat(payments).hasSize(1);
                    assertThat(cash).isEqualByComparingTo("30.00");
                    assertThat(cashShiftIdOf(payments.getFirst().getId())).isEqualTo(shift.id());
                } else {
                    assertThat(payments).isEmpty();
                    assertThat(cash).isEqualByComparingTo("0");
                }
                assertThat(cashMovementRepository.findByCashShift_Id(
                        shift.id(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isZero();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void seedsCashAuthoritiesOnlyForAdministratorAndCashier() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.code AS permission, r.code AS role
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'BILLING_CASH_%'
                """);

        assertThat(rows).extracting(row -> row.get("permission") + ":" + row.get("role"))
                .containsExactlyInAnyOrder(
                        "BILLING_CASH_MANAGE:ADMINISTRATOR",
                        "BILLING_CASH_MANAGE:CASHIER",
                        "BILLING_CASH_READ_ALL:ADMINISTRATOR");
    }

    private UUID cashShiftIdOf(UUID paymentId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            return payment.getCashShift() == null ? null : payment.getCashShift().getId();
        });
    }

    private User createUser() {
        int n = sequence.incrementAndGet();
        return userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "cash-user-" + n, "Cajero " + n,
                "cash-user-" + n + "@example.test", String.format("8%012d", n), "hash",
                UserStatus.ACTIVE, NOW, NOW));
    }

    private Patient createPatient() {
        int n = sequence.incrementAndGet();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-CASH-" + n);
        patient.setName("Paciente Caja");
        patient.setDpi(String.format("9%012d", n));
        patient.setBirthDate(LocalDate.of(1991, 4, 4));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-1111");
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
