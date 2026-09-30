package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.appointments.mapper.AppointmentMapper;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.appointments.service.PatientAppointmentServiceImpl;
import com.dentalcare.api.modules.appointments.service.AppointmentService;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientAppointmentController.class, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class, PatientAppointmentServiceImpl.class,
        AppointmentMapper.class})
class PatientAppointmentControllerSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean PatientRepository patients;
    @MockitoBean AppointmentRepository appointments;
    @MockitoBean AppointmentService appointmentService;
    @MockitoBean UserRepository users;

    private UUID userId;
    private Patient patient;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        appointment = appointment(patient);
    }

    @Test
    void patientListsOnlyAppointmentsSelectedForJwtLinkedPatient() throws Exception {
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .param("patientId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appointment.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[0].professional.fullName").value("Dra. Ana López"))
                .andExpect(jsonPath("$.content[0].patient").doesNotExist())
                .andExpect(jsonPath("$.content[0].professional.email").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(appointments).findByPatient_Id(eq(patient.getId()), any());
    }

    @Test
    void patientCanReadOwnedAppointment() throws Exception {
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));

        mockMvc.perform(get("/api/v1/patients/me/appointments/{id}", appointment.getId())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.getId().toString()));
    }

    @Test
    void foreignAndUnknownAppointmentsBothReturnSame404WithoutDisclosure() throws Exception {
        UUID foreignOrUnknownId = UUID.randomUUID();
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(foreignOrUnknownId, patient.getId()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/patients/me/appointments/{id}", foreignOrUnknownId)
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void endpointsRequirePatientRole() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me/appointments"))
                .andExpect(status().isUnauthorized());

        when(jwtService.parseAccessToken("staff-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("ROLE_SECRETARY")));
        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCreatesAppointmentFromJwtIdentityAndCannotSelectPatientId() throws Exception {
        UUID attackerSuppliedPatientId = UUID.randomUUID();
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointmentService.create(patient.getId(), appointment.getProfessional().getId(),
                appointment.getScheduledAt())).thenReturn(appointment);

        mockMvc.perform(post("/api/v1/patients/me/appointments")
                        .header("Authorization", "Bearer patient-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "patientId": "%s",
                                  "professionalId": "%s",
                                  "scheduledAt": "%s"
                                }
                                """.formatted(attackerSuppliedPatientId,
                                appointment.getProfessional().getId(), appointment.getScheduledAt())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        verify(appointmentService).create(
                patient.getId(), appointment.getProfessional().getId(), appointment.getScheduledAt());
    }

    @Test
    void createAndProfessionalCatalogRequirePatientRole() throws Exception {
        String body = """
                {"professionalId":"%s","scheduledAt":"%s"}
                """.formatted(appointment.getProfessional().getId(), appointment.getScheduledAt());

        mockMvc.perform(post("/api/v1/patients/me/appointments")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/me/appointments/professionals"))
                .andExpect(status().isUnauthorized());

        when(jwtService.parseAccessToken("staff-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("ROLE_SECRETARY")));
        mockMvc.perform(post("/api/v1/patients/me/appointments")
                        .header("Authorization", "Bearer staff-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/me/appointments/professionals")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientGetsMinimalProfessionalCatalog() throws Exception {
        User dentist = appointment.getProfessional();
        dentist.setEmail("private@example.test");
        dentist.setPasswordHash("secret-hash");
        patientToken("patient-token", userId);
        when(users.findActiveDentists()).thenReturn(List.of(dentist));

        mockMvc.perform(get("/api/v1/patients/me/appointments/professionals")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dentist.getId().toString()))
                .andExpect(jsonPath("$[0].fullName").value("Dra. Ana López"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].roles").doesNotExist());
    }

    @Test
    void createValidatesRequiredFields() throws Exception {
        patientToken("patient-token", userId);
        mockMvc.perform(post("/api/v1/patients/me/appointments")
                        .header("Authorization", "Bearer patient-token")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.professionalId").value("Professional id is required"))
                .andExpect(jsonPath("$.fieldErrors.scheduledAt").value("Appointment date and time are required"));
    }

    @Test
    void patientCancelsOwnedAppointmentSuccessfully() throws Exception {
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        Appointment cancelled = new Appointment(
                appointment.getId(), patient, appointment.getProfessional(), appointment.getScheduledAt(),
                AppointmentStatus.CANCELLED, appointment.getCreatedAt(), Instant.now());
        when(appointmentService.cancel(appointment)).thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.scheduledAt").value(appointment.getScheduledAt().toString()))
                .andExpect(jsonPath("$.professional.id").value(appointment.getProfessional().getId().toString()))
                .andExpect(jsonPath("$.professional.fullName").value("Dra. Ana López"))
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.professional.email").doesNotExist())
                .andExpect(jsonPath("$.professional.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.professional.roles").doesNotExist());

        verify(appointmentService).cancel(appointment);
        verify(appointments, never()).findById(any());
        verify(appointments, never()).delete(any());
        verify(appointments, never()).deleteById(any());
    }

    @Test
    void cancelDerivesIdentitySolelyFromJwtIgnoringCallerSuppliedBodyAndParams() throws Exception {
        UUID attackerSuppliedPatientId = UUID.randomUUID();
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        Appointment cancelled = new Appointment(
                appointment.getId(), patient, appointment.getProfessional(), appointment.getScheduledAt(),
                AppointmentStatus.CANCELLED, appointment.getCreatedAt(), Instant.now());
        when(appointmentService.cancel(appointment)).thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId())
                        .param("patientId", attackerSuppliedPatientId.toString())
                        .param("userId", attackerSuppliedPatientId.toString())
                        .header("Authorization", "Bearer patient-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "patientId": "%s",
                                  "status": "COMPLETED"
                                }
                                """.formatted(attackerSuppliedPatientId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(appointmentService).cancel(appointment);
        verify(appointments).findByIdAndPatient_Id(appointment.getId(), patient.getId());
    }

    @Test
    void cancelRequiresPatientRole() throws Exception {
        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId()))
                .andExpect(status().isUnauthorized());

        when(jwtService.parseAccessToken("staff-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("ROLE_SECRETARY")));
        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId())
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());

        when(jwtService.parseAccessToken("admin-token"))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of("ROLE_ADMINISTRATOR")));
        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId())
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelForeignAndUnknownAppointmentsBothReturnSame404WithoutDisclosure() throws Exception {
        UUID foreignOrUnknownId = UUID.randomUUID();
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(foreignOrUnknownId, patient.getId()))
                .thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", foreignOrUnknownId)
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.status").value(404));

        verify(appointmentService, never()).cancel(any());
        verify(appointments, never()).findById(any());
    }

    @Test
    void cancelReturns409WhenAppointmentIsNotScheduled() throws Exception {
        patientToken("patient-token", userId);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        when(appointmentService.cancel(appointment))
                .thenThrow(new ConflictException("Only scheduled appointments can be cancelled"));

        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", appointment.getId())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only scheduled appointments can be cancelled"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cancelRejectsMalformedAppointmentId() throws Exception {
        patientToken("patient-token", userId);

        mockMvc.perform(patch("/api/v1/patients/me/appointments/{id}/cancel", "not-a-valid-uuid")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listingAndDetailReflectCancelledStatus() throws Exception {
        patientToken("patient-token", userId);
        Appointment cancelledAppointment = new Appointment(
                appointment.getId(), patient, appointment.getProfessional(), appointment.getScheduledAt(),
                AppointmentStatus.CANCELLED, appointment.getCreatedAt(), appointment.getUpdatedAt());

        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(cancelledAppointment.getId(), patient.getId()))
                .thenReturn(Optional.of(cancelledAppointment));
        when(appointments.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(cancelledAppointment)));

        mockMvc.perform(get("/api/v1/patients/me/appointments/{id}", cancelledAppointment.getId())
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cancelledAppointment.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/patients/me/appointments")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(cancelledAppointment.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    private void patientToken(String token, UUID id) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(id, List.of("ROLE_PATIENT")));
    }

    private Appointment appointment(Patient owner) {
        User dentist = new User();
        dentist.setId(UUID.randomUUID());
        dentist.setFullName("Dra. Ana López");
        Instant scheduledAt = Instant.parse("2026-10-10T15:00:00Z");
        return new Appointment(UUID.randomUUID(), owner, dentist, scheduledAt,
                AppointmentStatus.SCHEDULED, scheduledAt.minusSeconds(3600), scheduledAt.minusSeconds(3600));
    }
}
