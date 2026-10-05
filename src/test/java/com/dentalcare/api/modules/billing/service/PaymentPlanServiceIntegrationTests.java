package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentPlanRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.InstallmentStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanResponse;
import com.dentalcare.api.modules.billing.dto.response.PaymentPlanViewStatus;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.mapper.PaymentPlanMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Installment;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.PaymentPlan;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentPlanRepository;
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
import java.time.ZoneId;
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
@Import({PaymentPlanServiceImpl.class, PaymentPlanMapper.class, BillingServiceImpl.class, BillingMapper.class,
        CashShiftServiceImpl.class, CashShiftMapper.class,
        com.dentalcare.api.modules.billing.ledger.ChargeLedger.class,
        PaymentPlanServiceIntegrationTests.ClockConfiguration.class})
class PaymentPlanServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private PaymentPlanRepository paymentPlanRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicInteger sequence = new AtomicInteger();

    @Test
    void partialUniqueIndexAllowsExactlyOneConcurrentActivePlan() throws Exception {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Ortodoncia", new BigDecimal("300.00")));
        LocalDate firstDueDate = LocalDate.now(CLINIC_ZONE);
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
                        paymentPlanService.create(patient.getId(), charge.id(), user.getId(),
                                new CreatePaymentPlanRequest(3, firstDueDate));
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
        Long active = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM billing_payment_plans
                WHERE charge_id = ?::uuid AND status = 'ACTIVE'
                """, Long.class, charge.id().toString());
        assertThat(active).isEqualTo(1L);
    }

    @Test
    void installmentsSumThePendingBalanceAndARealPaymentUpdatesDerivedStatus() {
        User user = createUser();
        Patient patient = createPatient();
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Limpieza", new BigDecimal("100.00")));
        LocalDate firstDueDate = LocalDate.now(CLINIC_ZONE).plusDays(1);

        PaymentPlanResponse created = paymentPlanService.create(patient.getId(), charge.id(), user.getId(),
                new CreatePaymentPlanRequest(3, firstDueDate));

        assertThat(created.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(created.installments()).extracting(installment -> installment.amount())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        BigDecimal storedSum = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM billing_installments WHERE plan_id = ?::uuid
                """, BigDecimal.class, created.id().toString());
        assertThat(storedSum).isEqualByComparingTo(created.totalAmount());
        List<BigDecimal> amountsBeforePayment = installmentAmounts(created.id());

        billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(charge.id(), new BigDecimal("33.33"), PaymentMethod.CARD), user.getId());

        assertThat(installmentAmounts(created.id())).isEqualTo(amountsBeforePayment);
        PaymentPlanResponse viewed = paymentPlanService.findActiveByCharge(patient.getId(), charge.id());
        assertThat(viewed.status()).isEqualTo(PaymentPlanViewStatus.ACTIVE);
        assertThat(viewed.paidAmount()).isEqualByComparingTo("33.33");
        assertThat(viewed.pendingAmount()).isEqualByComparingTo("66.67");
        assertThat(viewed.installments().get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(viewed.installments().get(1).status()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(viewed.installments().get(0).amount()).isEqualByComparingTo("33.33");
    }

    @Test
    void compositeChargePatientConstraintRejectsAMismatchedPatient() {
        User user = createUser();
        Patient patient = createPatient();
        Charge charge = chargeRepository.saveAndFlush(
                new Charge(UUID.randomUUID(), patient, "Consulta", new BigDecimal("80.00"), NOW));
        PaymentPlan plan = new PaymentPlan(
                UUID.randomUUID(), charge.getId(), UUID.randomUUID(), 2,
                new BigDecimal("80.00"), new BigDecimal("0.00"), LocalDate.of(2026, 11, 1),
                user.getId(), NOW);
        plan.addInstallment(new Installment(UUID.randomUUID(), 1, new BigDecimal("40.00"), LocalDate.of(2026, 11, 1)));
        plan.addInstallment(new Installment(UUID.randomUUID(), 2, new BigDecimal("40.00"), LocalDate.of(2026, 12, 1)));

        assertThatThrownBy(() -> paymentPlanRepository.saveAndFlush(plan))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentPlanRepository.findActiveByChargeIdAndPatientId(charge.getId(), patient.getId())).isEmpty();
    }

    @Test
    void seedsPlanManageOnlyForAdministratorAndCashier() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.code AS permission, r.code AS role
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'BILLING_PLAN_MANAGE'
                """);

        assertThat(rows).extracting(row -> row.get("permission") + ":" + row.get("role"))
                .containsExactlyInAnyOrder(
                        "BILLING_PLAN_MANAGE:ADMINISTRATOR",
                        "BILLING_PLAN_MANAGE:CASHIER");
    }

    private List<BigDecimal> installmentAmounts(UUID planId) {
        return jdbcTemplate.queryForList("""
                SELECT amount FROM billing_installments WHERE plan_id = ?::uuid ORDER BY number
                """, BigDecimal.class, planId.toString());
    }

    private User createUser() {
        int n = sequence.incrementAndGet();
        return userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "plan-user-" + n, "Cajero " + n,
                "plan-user-" + n + "@example.test", String.format("5%012d", n), "hash",
                UserStatus.ACTIVE, NOW, NOW));
    }

    private Patient createPatient() {
        int n = sequence.incrementAndGet();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-PL-" + n);
        patient.setName("Paciente Convenio");
        patient.setDpi(String.format("4%012d", n));
        patient.setBirthDate(LocalDate.of(1993, 2, 2));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-3333");
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
