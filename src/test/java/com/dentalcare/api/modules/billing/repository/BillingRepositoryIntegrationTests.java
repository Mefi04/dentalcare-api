package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class BillingRepositoryIntegrationTests {

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
    private ChargeRepository chargeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsChargesAndPaymentsAndListsThemInChronologicalOrder() {
        Patient patient = createPatient("PAC-BL-001", "9000000000301");
        Charge later = chargeRepository.saveAndFlush(charge(patient, "Limpieza dental", "250.00", NOW));
        Charge earlier = chargeRepository.saveAndFlush(
                charge(patient, "Consulta inicial", "150.50", NOW.minusSeconds(3600)));
        paymentRepository.saveAndFlush(payment(patient, earlier, PaymentKind.PAYMENT, "150.50", NOW));
        paymentRepository.saveAndFlush(payment(patient, null, PaymentKind.ADVANCE, "100.00", NOW.minusSeconds(60)));
        entityManager.clear();

        List<Charge> charges = chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId());
        List<Payment> payments = paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId());

        assertThat(charges).extracting(Charge::getId).containsExactly(earlier.getId(), later.getId());
        assertThat(charges.get(0).getConcept()).isEqualTo("Consulta inicial");
        assertThat(charges.get(0).getAmount()).isEqualByComparingTo("150.50");
        assertThat(charges.get(0).getPatient().getId()).isEqualTo(patient.getId());
        assertThat(payments).extracting(Payment::getKind)
                .containsExactly(PaymentKind.ADVANCE, PaymentKind.PAYMENT);
        assertThat(payments.get(0).getCharge()).isNull();
        assertThat(payments.get(1).getCharge().getId()).isEqualTo(earlier.getId());
        assertThat(payments.get(1).getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payments.get(1).getAmount()).isEqualByComparingTo("150.50");
    }

    @Test
    void listsOnlyTheRequestedPatientsRows() {
        Patient owner = createPatient("PAC-BL-002", "9000000000302");
        Patient other = createPatient("PAC-BL-003", "9000000000303");
        chargeRepository.saveAndFlush(charge(owner, "Extracción", "300.00", NOW));
        chargeRepository.saveAndFlush(charge(other, "Resina", "400.00", NOW));
        paymentRepository.saveAndFlush(payment(other, null, PaymentKind.ADVANCE, "50.00", NOW));
        entityManager.clear();

        assertThat(chargeRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(owner.getId()))
                .extracting(Charge::getConcept).containsExactly("Extracción");
        assertThat(paymentRepository.findByPatient_IdOrderByCreatedAtAscIdAsc(owner.getId())).isEmpty();
    }

    @Test
    void sumsOnlyPaymentsAppliedToTheCharge() {
        Patient patient = createPatient("PAC-BL-004", "9000000000304");
        Charge charge = chargeRepository.saveAndFlush(charge(patient, "Ortodoncia", "1000.00", NOW));

        assertThat(paymentRepository.sumAmountByChargeId(charge.getId())).isEqualByComparingTo("0");

        paymentRepository.saveAndFlush(payment(patient, charge, PaymentKind.PARTIAL_PAYMENT, "300.25", NOW));
        paymentRepository.saveAndFlush(payment(patient, charge, PaymentKind.PARTIAL_PAYMENT, "199.75", NOW));
        paymentRepository.saveAndFlush(payment(patient, null, PaymentKind.ADVANCE, "80.00", NOW));
        entityManager.clear();

        assertThat(paymentRepository.sumAmountByChargeId(charge.getId())).isEqualByComparingTo("500.00");
    }

    @Test
    void findsChargeForUpdateOnlyWhenItBelongsToThePatient() {
        Patient owner = createPatient("PAC-BL-005", "9000000000305");
        Patient other = createPatient("PAC-BL-006", "9000000000306");
        Charge charge = chargeRepository.saveAndFlush(charge(owner, "Corona", "900.00", NOW));
        entityManager.clear();

        assertThat(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), owner.getId())).isPresent();
        assertThat(chargeRepository.findByIdAndPatientIdForUpdate(charge.getId(), other.getId())).isEmpty();
    }

    @Test
    void rejectsPaymentAppliedToAnotherPatientsCharge() {
        Patient owner = createPatient("PAC-BL-007", "9000000000307");
        Patient other = createPatient("PAC-BL-008", "9000000000308");
        Charge charge = chargeRepository.saveAndFlush(charge(owner, "Endodoncia", "700.00", NOW));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(
                payment(other, charge, PaymentKind.PARTIAL_PAYMENT, "100.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveChargeAmount() {
        Patient patient = createPatient("PAC-BL-009", "9000000000309");

        assertThatThrownBy(() -> chargeRepository.saveAndFlush(charge(patient, "Consulta", "0.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankChargeConcept() {
        Patient patient = createPatient("PAC-BL-010", "9000000000310");

        assertThatThrownBy(() -> chargeRepository.saveAndFlush(charge(patient, "   ", "10.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativePaymentAmount() {
        Patient patient = createPatient("PAC-BL-011", "9000000000311");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(
                payment(patient, null, PaymentKind.ADVANCE, "-5.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAdvanceLinkedToCharge() {
        Patient patient = createPatient("PAC-BL-012", "9000000000312");
        Charge charge = chargeRepository.saveAndFlush(charge(patient, "Blanqueamiento", "500.00", NOW));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(
                payment(patient, charge, PaymentKind.ADVANCE, "100.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsChargePaymentWithoutCharge() {
        Patient patient = createPatient("PAC-BL-013", "9000000000313");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(
                payment(patient, null, PaymentKind.PAYMENT, "100.00", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsBillingPermissionsOnlyForJustifiedRoles() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.code, r.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code LIKE 'BILLING_%'
                """).getResultList();

        assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactlyInAnyOrder(
                "BILLING_READ:ADMINISTRATOR",
                "BILLING_READ:SECRETARY",
                "BILLING_READ:CASHIER",
                "BILLING_CHARGE_CREATE:ADMINISTRATOR",
                "BILLING_CHARGE_CREATE:CASHIER",
                "BILLING_PAYMENT_CREATE:ADMINISTRATOR",
                "BILLING_PAYMENT_CREATE:CASHIER",
                "BILLING_CASH_MANAGE:ADMINISTRATOR",
                "BILLING_CASH_MANAGE:CASHIER",
                "BILLING_CASH_READ_ALL:ADMINISTRATOR",
                "BILLING_RECEIPT_CREATE:ADMINISTRATOR",
                "BILLING_RECEIPT_CREATE:CASHIER",
                "BILLING_PLAN_MANAGE:ADMINISTRATOR",
                "BILLING_PLAN_MANAGE:CASHIER");
    }

    private Charge charge(Patient patient, String concept, String amount, Instant createdAt) {
        return new Charge(UUID.randomUUID(), patient, concept, new BigDecimal(amount), createdAt);
    }

    private Payment payment(Patient patient, Charge charge, PaymentKind kind, String amount, Instant createdAt) {
        return new Payment(UUID.randomUUID(), patient, charge, kind, PaymentMethod.CASH,
                new BigDecimal(amount), createdAt);
    }

    private Patient createPatient(String code, String dpi) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente Cuenta");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 6, 15));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-3030");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patientRepository.saveAndFlush(patient);
    }
}
