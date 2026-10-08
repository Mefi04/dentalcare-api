package com.dentalcare.api.security.ratelimit;

import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.reports.controller.DashboardReportController;
import com.dentalcare.api.modules.reports.service.DashboardReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiRateLimitStandaloneHttpTests {

    private MockMvc mockMvc;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private DashboardReportService dashboardReportService;

    @InjectMocks
    private ApiRateLimitInterceptor apiRateLimitInterceptor;

    @BeforeEach
    void setUp() {
        DashboardReportController controller = new DashboardReportController(dashboardReportService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(apiRateLimitInterceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        doThrow(new RateLimitExceededException(45))
                .when(rateLimitService).checkIp(eq(RateLimitPolicy.REPORTS), any());

        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .header("X-Forwarded-For", "192.168.1.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"));
    }

    @Test
    void shouldReturn400WhenPageIsTooLarge() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .param("page", "1001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenPageIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());
    }
}
