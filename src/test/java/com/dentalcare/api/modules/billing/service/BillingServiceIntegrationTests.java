package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CreateChargeRequest;
import com.dentalcare.api.modules.billing.dto.request.CreatePaymentRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.AccountStatementResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeResponse;
import com.dentalcare.api.modules.billing.dto.response.ChargeStatus;
import com.dentalcare.api.modules.billing.dto.response.PaymentResponse;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real service against PostgreSQL without a test-managed transaction, so every service call
 * commits on its own and concurrent calls really compete for the charge row lock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BillingServiceImpl.class, CashShiftServiceImpl.class, BillingMapper.class, CashShiftMapper.class,
        com.dentalcare.api.modules.billing.ledger.ChargeLedger.class,
        BillingServiceIntegrationTests.ClockConfiguration.class})
class BillingServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private BillingService billingService;

    @Autowired
    private CashShiftService cashShiftService;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    private static final AtomicInteger users = new AtomicInteger();

    private UUID cashierId;

    @Test
    void concurrentPaymentsNeverExceedTheChargeAmount() throws Exception {
        openCashierShift();
        Patient patient = createPatient("PAC-BS-001", "9000000000401");
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Ortodoncia", new BigDecimal("100.00")));
        int attempts = 5;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);

        long accepted = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        pay(patient, charge.id(), "30.00");
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

        assertThat(accepted).isEqualTo(3);
        assertThat(paymentRepository.sumAmountByChargeId(charge.id())).isEqualByComparingTo("90.00");
        ChargeResponse reloaded = billingService.findAccountStatement(patient.getId()).charges().get(0);
        assertThat(reloaded.pending()).isEqualByComparingTo("10.00");
        assertThat(reloaded.status()).isEqualTo(ChargeStatus.PARTIALLY_PAID);
    }

    @Test
    void registersMovementsAndReadsConsistentStatementFromDatabase() {
        openCashierShift();
        Patient patient = createPatient("PAC-BS-002", "9000000000402");
        ChargeResponse charge = billingService.createCharge(patient.getId(),
                new CreateChargeRequest("Limpieza", new BigDecimal("250.00")));

        PaymentResponse partial = pay(patient, charge.id(), "100.00");
        PaymentResponse settlement = pay(patient, charge.id(), "150.00");
        PaymentResponse advance = pay(patient, null, "40.00");

        assertThat(partial.kind()).isEqualTo(PaymentKind.PARTIAL_PAYMENT);
        assertThat(settlement.kind()).isEqualTo(PaymentKind.PAYMENT);
        assertThat(advance.kind()).isEqualTo(PaymentKind.ADVANCE);
        assertThat(advance.chargeId()).isNull();

        AccountStatementResponse statement = billingService.findAccountStatement(patient.getId());
        assertThat(statement.summary().charged()).isEqualByComparingTo("250.00");
        assertThat(statement.summary().paid()).isEqualByComparingTo("250.00");
        assertThat(statement.summary().advances()).isEqualByComparingTo("40.00");
        assertThat(statement.summary().balance()).isEqualByComparingTo("-40.00");
        assertThat(statement.charges()).singleElement().satisfies(reloaded -> {
            assertThat(reloaded.status()).isEqualTo(ChargeStatus.PAID);
            assertThat(reloaded.pending()).isEqualByComparingTo("0.00");
        });
        assertThat(statement.payments()).extracting(PaymentResponse::id)
                .containsExactlyInAnyOrder(partial.id(), settlement.id(), advance.id());

        assertThatThrownBy(() -> pay(patient, charge.id(), "0.01"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Charge is already paid");
    }

    @Test
    void paymentCannotTargetAnotherPatientsCharge() {
        openCashierShift();
        Patient owner = createPatient("PAC-BS-003", "9000000000403");
        Patient other = createPatient("PAC-BS-004", "9000000000404");
        ChargeResponse charge = billingService.createCharge(owner.getId(),
                new CreateChargeRequest("Corona", new BigDecimal("900.00")));

        assertThatThrownBy(() -> pay(other, charge.id(), "10.00"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Charge not found");

        assertThat(paymentRepository.sumAmountByChargeId(charge.id())).isEqualByComparingTo("0");
        assertThat(billingService.findAccountStatement(other.getId()).payments()).isEmpty();
    }

    private PaymentResponse pay(Patient patient, UUID chargeId, String amount) {
        return billingService.registerPayment(patient.getId(),
                new CreatePaymentRequest(chargeId, new BigDecimal(amount), PaymentMethod.CASH), cashierId);
    }

    private void openCashierShift() {
        int n = users.incrementAndGet();
        String cui = String.format("7%012d", n);
        User user = userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "billing-cashier-" + n, "Cajero",
                "billing-cashier-" + n + "@example.test", cui, "hash",
                UserStatus.ACTIVE, NOW, NOW));
        cashierId = user.getId();
        cashShiftService.open(cashierId, new OpenCashShiftRequest(new BigDecimal("0.00"), null));
    }

    private Patient createPatient(String code, String dpi) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente Caja");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1985, 3, 20));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-4040");
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
