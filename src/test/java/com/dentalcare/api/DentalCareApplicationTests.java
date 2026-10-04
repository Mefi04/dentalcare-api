package com.dentalcare.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.dentalcare.api.security.jwt.JwtService;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.appointments.repository.AdministrativeAppointmentRepository;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.medicalhistory.repository.MedicalHistoryRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.reports.repository.JpaDashboardMetricsRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "dentalcare.cors.allowed-origin=http://localhost:3000"
})
class DentalCareApplicationTests {

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    PatientRepository patientRepository;

    @MockitoBean
    AppointmentRepository appointmentRepository;

    @MockitoBean
    AdministrativeAppointmentRepository administrativeAppointmentRepository;

    @MockitoBean
    com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository appointmentRequestRepository;

    @MockitoBean
    com.dentalcare.api.modules.appointments.repository.WaitingRoomRepository waitingRoomRepository;

    @MockitoBean
    MedicalHistoryRepository medicalHistoryRepository;

    @MockitoBean
    ChargeRepository chargeRepository;

    @MockitoBean
    PaymentRepository paymentRepository;

    @MockitoBean
    TreatmentPlanRepository treatmentPlanRepository;

    @MockitoBean
    com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository treatmentBudgetRepository;

    @MockitoBean
    com.dentalcare.api.modules.treatments.repository.TreatmentConsentRepository treatmentConsentRepository;

    @MockitoBean
    JpaDashboardMetricsRepository dashboardMetricsRepository;

    @MockitoBean
    com.dentalcare.api.modules.auth.repository.RefreshSessionRepository refreshSessionRepository;

    @MockitoBean
    com.dentalcare.api.modules.auth.repository.PasswordRecoveryTokenRepository passwordRecoveryTokenRepository;

    @MockitoBean
    com.dentalcare.api.modules.inventory.repository.InventoryItemRepository inventoryItemRepository;

    @MockitoBean
    com.dentalcare.api.modules.inventory.repository.InventoryMovementRepository inventoryMovementRepository;

    @MockitoBean
    com.dentalcare.api.modules.sterilization.repository.SterilizationProtocolRepository sterilizationProtocolRepository;

    @MockitoBean
    com.dentalcare.api.modules.sterilization.repository.SterilizationCycleRepository sterilizationCycleRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.ClinicalAttentionRepository clinicalAttentionRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDiagnosisRepository clinicalDiagnosisRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.ClinicalEvolutionNoteRepository clinicalEvolutionNoteRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.OdontogramFindingRepository odontogramFindingRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository clinicalDocumentRepository;

    @MockitoBean
    com.dentalcare.api.modules.clinicalrecords.repository.ClinicalPreparationRepository clinicalPreparationRepository;

    @MockitoBean
    com.dentalcare.api.modules.prescriptions.repository.PrescriptionRepository prescriptionRepository;

    @MockitoBean
    com.dentalcare.api.modules.treatments.repository.TreatmentProcedureRepository treatmentProcedureRepository;

    @MockitoBean
    com.dentalcare.api.modules.inventory.repository.SupplierRepository supplierRepository;

    @MockitoBean
    com.dentalcare.api.modules.inventory.repository.PurchaseRepository purchaseRepository;

    @MockitoBean
    com.dentalcare.api.modules.inventory.repository.PurchaseItemRepository purchaseItemRepository;

    @Test
    void contextLoads() {
    }
}
