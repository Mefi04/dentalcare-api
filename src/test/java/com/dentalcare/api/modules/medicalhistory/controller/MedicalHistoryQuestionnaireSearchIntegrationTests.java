package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.AssignMedicalHistoryQuestionnaireRequest;
import com.dentalcare.api.modules.medicalhistory.dto.request.CreateMedicalHistoryTemplateRequest;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryAnswerType;
import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireSource;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MedicalHistoryQuestionnaireSearchIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("INITIAL_ADMIN_ENABLED", () -> "false");
        registry.add("FRONTEND_URL", () -> "http://localhost:3000");
    }

    @Autowired MockMvc mockMvc;
    @Autowired MedicalHistoryWorkflowService service;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @MockitoBean JwtService jwtService;

    private UUID patientId;
    private UUID webQuestionnaireId;
    private Instant beforeCreation;
    private Instant afterCreation;

    @BeforeEach
    void setUp() {
        User dentist = user("dentist154", "8000000000154", "Dentist 154");
        Patient webPatient = patient(user("patient154a", "8000000000155", "Patient Web"), "PAC-154-A", "7000000000154");
        Patient appPatient = patient(user("patient154b", "8000000000156", "Patient App"), "PAC-154-B", "7000000000155");
        publishTemplate(dentist.getId());
        beforeCreation = Instant.now().minusSeconds(1);
        webQuestionnaireId = service.assign(webPatient.getId(), new AssignMedicalHistoryQuestionnaireRequest(QuestionnaireSource.WEB, null, null), dentist.getId()).id();
        service.assign(appPatient.getId(), new AssignMedicalHistoryQuestionnaireRequest(QuestionnaireSource.APP, null, null), dentist.getId());
        afterCreation = Instant.now().plusSeconds(1);
        patientId = webPatient.getId();
        when(jwtService.parseAccessToken("reader")).thenReturn(new JwtService.AccessTokenClaims(dentist.getId(), List.of("MEDICAL_HISTORY_STATUS_READ")));
        when(jwtService.parseAccessToken("no-permission")).thenReturn(new JwtService.AccessTokenClaims(dentist.getId(), List.of("MEDICAL_HISTORY_CLINICAL_READ")));
    }

    @Test
    void searchesEverySupportedFilterAgainstPostgreSqlAndReturnsMatchingRows() throws Exception {
        mockMvc.perform(get("/api/v1/medical-history/questionnaires").header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        expectOne("?patientId=" + patientId);
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?status=DRAFT").header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        expectOne("?source=WEB");
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?from=" + beforeCreation).header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?to=" + afterCreation).header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?from=" + beforeCreation + "&to=" + afterCreation).header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        expectOne("?patientId=" + patientId + "&status=DRAFT&source=WEB&from=" + beforeCreation + "&to=" + afterCreation);
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?source=PAPER").header("Authorization", "Bearer reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void preservesAuthenticationAuthorizationAndPaginationValidation() throws Exception {
        mockMvc.perform(get("/api/v1/medical-history/questionnaires")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medical-history/questionnaires").header("Authorization", "Bearer no-permission")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?size=0").header("Authorization", "Bearer reader")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/medical-history/questionnaires?from=" + afterCreation + "&to=" + beforeCreation).header("Authorization", "Bearer reader")).andExpect(status().isBadRequest());
    }

    private void expectOne(String query) throws Exception {
        mockMvc.perform(get("/api/v1/medical-history/questionnaires" + query).header("Authorization", "Bearer reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(webQuestionnaireId.toString()));
    }

    private void publishTemplate(UUID dentistId) {
        var question = new CreateMedicalHistoryTemplateRequest.Question("NOTE", "Note", MedicalHistoryAnswerType.LONG_TEXT, false, false, 100, BigDecimal.ZERO, null, null, null);
        var request = new CreateMedicalHistoryTemplateRequest("GENERAL_154", "General", "Search fixture", List.of(new CreateMedicalHistoryTemplateRequest.Section("GENERAL", "General", null, List.of(question))));
        var draft = service.createTemplate(request, dentistId);
        service.publishTemplate(draft.versionId(), dentistId);
    }

    private User user(String username, String cui, String name) {
        Instant now = Instant.now();
        return userRepository.saveAndFlush(new User(UUID.randomUUID(), username, name, username + "@example.test", cui, "hash", UserStatus.ACTIVE, now, now));
    }

    private Patient patient(User user, String code, String dpi) {
        Instant now = Instant.now();
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName(user.getFullName());
        patient.setDpi(dpi);
        patient.setUser(user);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("55550000");
        patient.setCreatedAt(now);
        patient.setUpdatedAt(now);
        return patientRepository.saveAndFlush(patient);
    }
}
