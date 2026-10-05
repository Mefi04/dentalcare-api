package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.billing.dto.response.ReceiptResponse;
import com.dentalcare.api.modules.billing.mapper.ReceiptMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.model.Receipt;
import com.dentalcare.api.modules.billing.model.ReceiptStatus;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.repository.ReceiptRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@Import({ReceiptServiceImpl.class, ReceiptMapper.class, ReceiptServiceIntegrationTests.ClockConfiguration.class})
class ReceiptServiceIntegrationTests {

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
    private ReceiptService receiptService;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClinicSettingsService clinicSettingsService;

    private static final AtomicInteger sequence = new AtomicInteger();

    @Test
    void sequenceAssignsUniqueIncreasingNumbersMatchingThePayment() {
        User user = createUser();
        Patient patient = createPatient();
        Charge charge = chargeRepository.saveAndFlush(
                new Charge(UUID.randomUUID(), patient, "Limpieza", new BigDecimal("120.00"), NOW));
        Payment charged = paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(), patient, charge, PaymentKind.PARTIAL_PAYMENT, PaymentMethod.CARD,
                new BigDecimal("40.00"), NOW));
        Payment advance = paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(), patient, null, PaymentKind.ADVANCE, PaymentMethod.TRANSFER,
                new BigDecimal("15.50"), NOW));

        ReceiptResponse first = receiptService.issue(patient.getId(), charged.getId(), user.getId());
        ReceiptResponse second = receiptService.issue(patient.getId(), advance.getId(), user.getId());

        assertThat(first.receiptNumber()).isPositive();
        assertThat(second.receiptNumber()).isGreaterThan(first.receiptNumber());
        assertThat(first.receiptNumber()).isNotEqualTo(second.receiptNumber());
        assertThat(first.patientId()).isEqualTo(patient.getId());
        assertThat(first.paymentId()).isEqualTo(charged.getId());
        assertThat(first.concept()).isEqualTo("Limpieza");
        assertThat(first.amount()).isEqualByComparingTo("40.00");
        assertThat(first.method()).isEqualTo(PaymentMethod.CARD);
        assertThat(first.status()).isEqualTo(ReceiptStatus.ISSUED);
        assertThat(first.issuedByUserId()).isEqualTo(user.getId());
        assertThat(second.concept()).isEqualTo("Anticipo");
        assertThat(second.amount()).isEqualByComparingTo("15.50");
        assertThat(second.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(second.patientId()).isEqualTo(patient.getId());
    }

    @Test
    void concurrentIssueOfTheSamePaymentSucceedsExactlyOnce() throws Exception {
        User user = createUser();
        Patient patient = createPatient();
        Payment payment = paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(), patient, null, PaymentKind.ADVANCE, PaymentMethod.CASH,
                new BigDecimal("20.00"), NOW));
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
                        receiptService.issue(patient.getId(), payment.getId(), user.getId());
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
        Long stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_receipts WHERE payment_id = ?::uuid",
                Long.class,
                payment.getId().toString());
        assertThat(stored).isEqualTo(1L);
        assertThat(receiptRepository.findByPaymentId(payment.getId())).isPresent();
    }

    @Test
    void compositePaymentPatientConstraintRejectsAMismatchedPatient() {
        User user = createUser();
        Patient patient = createPatient();
        Payment payment = paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(), patient, null, PaymentKind.ADVANCE, PaymentMethod.CHECK,
                new BigDecimal("12.00"), NOW));
        long number = receiptRepository.nextReceiptNumber().longValue();

        assertThatThrownBy(() -> receiptRepository.saveAndFlush(new Receipt(
                UUID.randomUUID(),
                number,
                payment.getId(),
                UUID.randomUUID(),
                "Anticipo",
                payment.getAmount(),
                payment.getMethod(),
                user.getId(),
                NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(receiptRepository.findByPaymentId(payment.getId())).isEmpty();
    }

    @Test
    void seedsReceiptCreateOnlyForAdministratorAndCashier() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.code AS permission, r.code AS role
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'BILLING_RECEIPT_CREATE'
                """);

        assertThat(rows).extracting(row -> row.get("permission") + ":" + row.get("role"))
                .containsExactlyInAnyOrder(
                        "BILLING_RECEIPT_CREATE:ADMINISTRATOR",
                        "BILLING_RECEIPT_CREATE:CASHIER");
    }

    private User createUser() {
        int n = sequence.incrementAndGet();
        return userRepository.saveAndFlush(new User(
                UUID.randomUUID(), "receipt-user-" + n, "Cajero " + n,
                "receipt-user-" + n + "@example.test", String.format("7%012d", n), "hash",
                UserStatus.ACTIVE, NOW, NOW));
    }

    private Patient createPatient() {
        int n = sequence.incrementAndGet();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("PAC-RC-" + n);
        patient.setName("Paciente Recibo");
        patient.setDpi(String.format("6%012d", n));
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
