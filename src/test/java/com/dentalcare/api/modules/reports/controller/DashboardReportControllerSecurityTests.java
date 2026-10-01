package com.dentalcare.api.modules.reports.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.reports.dto.response.AppointmentMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.BillingMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.DashboardReportResponse;
import com.dentalcare.api.modules.reports.dto.response.PatientMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.ReportPeriodResponse;
import com.dentalcare.api.modules.reports.service.DashboardReportService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardReportController.class,
        properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class DashboardReportControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardReportService dashboardReportService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void dashboardRequiresAuthenticationAndAnAllowedStaffRole() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard"))
                .andExpect(status().isUnauthorized());

        token("patient-token", "ROLE_PATIENT");
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden());

        token("dentist-token", "ROLE_DENTIST");
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .header("Authorization", "Bearer dentist-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorSecretaryAndCashierCanReadDashboard() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(dashboardReportService.getDashboard(from, to)).thenReturn(response(from, to));

        for (String role : List.of("ADMINISTRATOR", "SECRETARY", "CASHIER")) {
            String accessToken = role.toLowerCase() + "-token";
            token(accessToken, "ROLE_" + role);
            mockMvc.perform(get("/api/v1/reports/dashboard")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period.from").value("2026-09-01"))
                    .andExpect(jsonPath("$.patients.total").value(30))
                    .andExpect(jsonPath("$.appointments.completed").value(7))
                    .andExpect(jsonPath("$.billing.paymentsReceivedInPeriod").value(850.00));
        }

        verify(dashboardReportService, org.mockito.Mockito.times(3)).getDashboard(from, to);
    }

    @Test
    void invalidDateAndBusinessValidationUseStandardErrors() throws Exception {
        token("administrator-token", "ROLE_ADMINISTRATOR");

        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .queryParam("from", "not-a-date")
                        .header("Authorization", "Bearer administrator-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request is malformed or contains an invalid value"));

        LocalDate from = LocalDate.of(2026, 10, 2);
        LocalDate to = LocalDate.of(2026, 10, 1);
        when(dashboardReportService.getDashboard(from, to))
                .thenThrow(new BadRequestException("Report start date must not be after end date"));
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header("Authorization", "Bearer administrator-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Report start date must not be after end date"));
    }

    private void token(String token, String... authorities) {
        when(jwtService.parseAccessToken(token))
                .thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(), List.of(authorities)));
    }

    private DashboardReportResponse response(LocalDate from, LocalDate to) {
        return new DashboardReportResponse(
                new ReportPeriodResponse(from, to, Instant.parse("2026-10-01T12:00:00Z")),
                new PatientMetricsResponse(30, 3),
                new AppointmentMetricsResponse(10, 2, 7, 1),
                new BillingMetricsResponse(new BigDecimal("1000.00"), new BigDecimal("850.00"),
                        new BigDecimal("500.00"), new BigDecimal("0.00")));
    }
}
