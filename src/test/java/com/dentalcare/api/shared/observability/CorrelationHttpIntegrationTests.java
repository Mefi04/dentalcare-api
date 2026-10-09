package com.dentalcare.api.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dentalcare.api.config.MonitoringMetricsConfig;
import jakarta.servlet.*;
import java.io.IOException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CorrelationHttpIntegrationTests.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "spring.liquibase.enabled=false"})
class CorrelationHttpIntegrationTests {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
    @Import({CorrelationIdFilter.class, MonitoringMetricsConfig.class, Probe.class})
    static class App {
        @Bean FilterRegistrationBean<Filter> failingFilter() {
            var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/synthetic/filter"))
                    throw new ServletException("Synthetic filter failure");
                chain.doFilter(request, response);
            });
            registration.setOrder(0);
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            return registration;
        }
    }
    @RestController static class Probe {
        @GetMapping("/synthetic/status/{code}") ResponseEntity<Void> status(@PathVariable int code) {
            return ResponseEntity.status(code).build();
        }
        @GetMapping("/synthetic/normal") String normal() { return "ok"; }
        @GetMapping("/synthetic/controller") String failed() { throw new IllegalStateException("Synthetic failure"); }
        @ExceptionHandler(IllegalStateException.class) ResponseEntity<Void> resolved() {
            return ResponseEntity.status(422).build();
        }
    }
    @Autowired TestRestTemplate client;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;
    Logger logger;
    ListAppender<ILoggingEvent> capture;
    @BeforeEach void capture() {
        // Do not let the test client retry 429/503 and manufacture additional requests.
        client.getRestTemplate().setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        logger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        capture = new ListAppender<>(); capture.start(); logger.addAppender(capture);
    }
    @AfterEach void release() { logger.detachAppender(capture); capture.stop(); assertNull(MDC.get("request_id")); }
    private java.util.List<ILoggingEvent> events(String id) {
        return capture.list.stream().filter(event -> id.equals(event.getMDCPropertyMap().get("request_id"))).toList();
    }
    private Object status(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals("status"))
            .map(pair -> pair.value).findFirst().orElse(null);
    }
    @Test void filterExceptionUsesContainerErrorStatusAndSameServerId() {
        double before = window("5xx");
        var response = client.getForEntity("/synthetic/filter", String.class);
        assertEquals(500, response.getStatusCode().value());
        String id = response.getHeaders().getFirst("X-Request-ID");
        assertNotNull(id); assertDoesNotThrow(() -> java.util.UUID.fromString(id));
        var events = events(id);
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> Integer.valueOf(500).equals(status(event))));
        assertTrue(events.stream().allMatch(event -> status(event) == null || Integer.valueOf(500).equals(status(event))));
        assertEquals(before + 1, window("5xx"));
    }
    private double window(String status) {
        return registry.get("dentalcare.http.window.requests").tags("route", "application", "status", status).gauge().value();
    }
    @Test void realHttpBurstIsVisibleWithoutScrapingOrWarmingItsSeries() {
        for (int code : new int[]{401, 403, 429}) {
            for (int i = 0; i < 40; i++)
                assertEquals(code, client.getForEntity("/synthetic/status/" + code, String.class).getStatusCode().value());
        }
        assertEquals(40, window("401"));
        assertEquals(40, window("403"));
        assertEquals(40, window("429"));
    }
    @Test void resolvedControllerFailureKeepsItsActualStatus() {
        var response = client.getForEntity("/synthetic/controller", String.class);
        assertEquals(422, response.getStatusCode().value());
        var events = events(response.getHeaders().getFirst("X-Request-ID"));
        assertEquals(1, events.size()); assertEquals(422, status(events.getFirst()));
    }
    @Test void normalRequestsHaveCorrectStatusAndIndependentIds() {
        var first = client.getForEntity("/synthetic/normal", String.class);
        var second = client.getForEntity("/synthetic/normal", String.class);
        String id = first.getHeaders().getFirst("X-Request-ID");
        assertNotEquals(id, second.getHeaders().getFirst("X-Request-ID"));
        assertEquals(200, status(events(id).getFirst()));
    }
    @Test void escapedExceptionPreservesAlreadyDeterminedErrorStatus() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        assertThrows(IOException.class, () -> new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
            response.setStatus(503); throw new IOException("Synthetic failure");
        }));
        assertEquals(503, status(events(response.getHeader("X-Request-ID")).getFirst()));
        assertNull(MDC.get("request_id"));
    }
}
