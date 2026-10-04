package com.dentalcare.api.modules.billing.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.billing.mapper.BillingMapper;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.billing.service.BillingServiceImpl;
import com.dentalcare.api.modules.billing.service.CashShiftService;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PatientBillingController.class, BillingController.class},
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class, BillingServiceImpl.class, BillingMapper.class,
        com.dentalcare.api.modules.billing.ledger.ChargeLedger.class})
class PatientBillingControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PatientRepository patients;

    @MockitoBean
    private ChargeRepository charges;

    @MockitoBean
    private PaymentRepository payments;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private CashShiftService cashShiftService;

    @MockitoBean
    private com.dentalcare.api.modules.billing.repository.ChargeAdjustmentRepository chargeAdjustmentRepository;

    @MockitoBean
    private com.dentalcare.api.modules.billing.repository.RefundRepository refundRepository;

    private UUID userId;
    private Patient patient;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
    }

    @Test
    void patientReadsOnlyStatementOfJwtLinkedPatient() throws Exception {
        UUID attackerSuppliedPatientId = UUID.randomUUID();
        Charge charge = new Charge(UUID.randomUUID(), patient, "Limpieza", new BigDecimal("300.00"), NOW);
        Payment payment = new Payment(UUID.randomUUID(), patient, charge, PaymentKind.PARTIAL_PAYMENT,
                PaymentMethod.CARD, new BigDecimal("100.00"), NOW);
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(charges.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).thenReturn(List.of(charge));
        when(payments.findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId())).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/v1/patients/me/account-statement")
                        .param("patientId", attackerSuppliedPatientId.toString())
                        .param("userId", attackerSuppliedPatientId.toString())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()))
                .andExpect(jsonPath("$.charges[0].concept").value("Limpieza"))
                .andExpect(jsonPath("$.charges[0].status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.payments[0].chargeId").value(charge.getId().toString()))
                .andExpect(jsonPath("$.payments[0].method").value("CARD"))
                .andExpect(jsonPath("$.summary.balance").value(200.0));

        verify(patients).findByUser_Id(userId);
        verify(charges).findByPatient_IdOrderByCreatedAtAscIdAsc(patient.getId());
        verify(charges, never()).findByPatient_IdOrderByCreatedAtAscIdAsc(attackerSuppliedPatientId);
        verify(patients, never()).existsById(any());
    }

    @Test
    void patientCannotReadAnotherPatientsStatementThroughAdministrativeRoute() throws Exception {
        patientToken("patient-token", userId);

        mockMvc.perform(get("/api/v1/patients/{patientId}/account-statement", UUID.randomUUID())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(patients, charges, payments);
    }

    @Test
    void selfServiceRequiresAuthenticationAndPatientRole() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/account-statement"))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("ROLE_CASHIER", "ROLE_ADMINISTRATOR", "ROLE_SECRETARY")) {
            when(jwtService.parseAccessToken("staff-token-" + role))
                    .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(role, "BILLING_READ")));
            mockMvc.perform(get("/api/v1/patients/me/account-statement")
                            .header("Authorization", "Bearer staff-token-" + role))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(patients, charges, payments);
    }

    @Test
    void userWithoutLinkedPatientReceivesNotFound() throws Exception {
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/patients/me/account-statement")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient not found"));

        verifyNoInteractions(charges, payments);
    }

    private void patientToken(String token, UUID id) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(id, List.of("ROLE_PATIENT")));
    }
}
