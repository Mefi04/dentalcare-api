package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.ConfirmFirstAppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.model.ProfessionalWorkInterval;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.appointments.repository.ProfessionalWorkIntervalRepository;
import com.dentalcare.api.modules.appointments.service.FirstAppointmentConfirmationService;
import com.dentalcare.api.modules.appointments.service.FirstAppointmentSchedulingOptionsService;
import com.dentalcare.api.modules.appointments.service.AppointmentService;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.dentalcare.api.security.jwt.JwtService;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PublicFirstAppointmentIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("INITIAL_ADMIN_ENABLED", () -> "false");
        registry.add("FRONTEND_URL", () -> "http://localhost:3000");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProfessionalPublicProfileRepository profiles;
    @Autowired ProfessionalWorkIntervalRepository work;
    @Autowired AppointmentRequestRepository requests;
    @Autowired FirstAppointmentConfirmationService confirmation;
    @Autowired FirstAppointmentSchedulingOptionsService options;
    @Autowired AppointmentService appointmentService;
    @MockitoBean JwtService jwtService;

    @Test
    void pendingRequestHasNoTokenOrAppointmentAndReceptionConfirmsByTelephone() throws Exception {
        Instant now = Instant.now();
        var dentist = new User(UUID.randomUUID(), "general-" + UUID.randomUUID(), "Dr General",
                "general-" + UUID.randomUUID() + "@test.local", "1111222233334", "hash",
                UserStatus.ACTIVE, now, now);
        dentist.setRoles(Set.of(roles.findByCode("DENTIST").orElseThrow()));
        users.saveAndFlush(dentist);
        var actor = new User(UUID.randomUUID(), "secretary-" + UUID.randomUUID(), "Reception",
                "secretary-" + UUID.randomUUID() + "@test.local", "1111222233335", "hash",
                UserStatus.ACTIVE, now, now);
        actor.setRoles(Set.of(roles.findByCode("SECRETARY").orElseThrow()));
        users.saveAndFlush(actor);
        var profile = new ProfessionalPublicProfile(UUID.randomUUID(), dentist, "REG-1",
                "Odontología general", "General dentist", 5, null, null, true,
                actor.getId(), actor.getId(), now, now);
        profile.setServiceCode(ProfessionalServiceCode.GENERAL_DENTISTRY);
        profiles.saveAndFlush(profile);
        var local = ZonedDateTime.now(ZoneId.of("America/Guatemala")).plusDays(3)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        work.saveAndFlush(new ProfessionalWorkInterval(UUID.randomUUID(), dentist.getId(),
                local.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(17, 0)));

        String payload = json.writeValueAsString(Map.ofEntries(
                Map.entry("fullName", "Public Visitor"), Map.entry("cui", "1234567890123"),
                Map.entry("birthDate", "1990-01-01"), Map.entry("gender", "FEMALE"),
                Map.entry("phone", "+502 5555-0199"), Map.entry("department", "Guatemala"),
                Map.entry("municipality", "Guatemala"), Map.entry("address", "Zona 1"),
                Map.entry("emergencyName", "Emergency Contact"),
                Map.entry("emergencyPhone", "+502 5555-0198"),
                Map.entry("requestedAt", local.toInstant().toString()),
                Map.entry("privacyAccepted", true), Map.entry("privacyNoticeVersion", "2026-10")));
        var response = mvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.conversationToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID requestId = UUID.fromString(json.readTree(response).path("requestId").asText());
        String secondPayload = payload.replace("Public Visitor", "Second Visitor")
                .replace("1234567890123", "1234567890124");
        var secondResponse = mvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(secondPayload))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID secondId = UUID.fromString(json.readTree(secondResponse).path("requestId").asText());
        var pending = requests.findDetailedById(requestId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(pending.getAppointment()).isNull();

        var confirmed = confirmation.confirm(actor.getId(), requestId,
                new ConfirmFirstAppointmentRequest(dentist.getId(), local.toInstant(), true));
        assertThat(confirmed.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(confirmed.appointmentId()).isNotNull();
        assertThat(confirmation.confirm(actor.getId(), requestId,
                new ConfirmFirstAppointmentRequest(dentist.getId(), local.toInstant(), true))
                .appointmentId()).isEqualTo(confirmed.appointmentId());
        assertThat(requests.findById(secondId).orElseThrow().getStatus())
                .isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        var secondOptions = options.forRequest(secondId);
        assertThat(secondOptions.conflict()).isTrue();
        assertThat(secondOptions.alternatives()).isNotEmpty();
        assertThatThrownBy(() -> appointmentService.createPublic("Overlapping Visitor",
                "+50255550100", dentist.getId(), local.toInstant().plusSeconds(900)))
                .isInstanceOf(ConflictException.class);
        mvc.perform(get("/api/v1/public/appointment-requests/{id}/conversation", requestId))
                .andExpect(status().isUnauthorized());
    }
}
