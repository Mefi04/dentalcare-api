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
import com.dentalcare.api.modules.reports.repository.DashboardMetricsRepository;
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
    MedicalHistoryRepository medicalHistoryRepository;

    @MockitoBean
    ChargeRepository chargeRepository;

    @MockitoBean
    PaymentRepository paymentRepository;

    @MockitoBean
    DashboardMetricsRepository dashboardMetricsRepository;

    @MockitoBean
    com.dentalcare.api.modules.auth.repository.RefreshSessionRepository refreshSessionRepository;

    @Test
    void contextLoads() {
    }
}
