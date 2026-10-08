package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.config.CorsConfig;
import com.dentalcare.api.config.SecurityConfig;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.RestAccessDeniedHandler;
import com.dentalcare.api.security.handler.RestAuthenticationEntryPoint;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MedicalHistoryQuestionnaireController.class,
        PatientMedicalHistoryQuestionnaireController.class}, properties = "FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class MedicalHistoryWorkflowControllerSecurityTests {

    @Autowired MockMvc mockMvc;
    @MockitoBean MedicalHistoryWorkflowService service;
    @MockitoBean JwtService jwtService;

    @Test
    void onlyValidationPermissionCanValidateClinicalHistory() throws Exception {
        UUID patientId=UUID.randomUUID();UUID questionnaireId=UUID.randomUUID();
        token("assistant","ROLE_ASSISTANT","MEDICAL_HISTORY_REVIEW");
        mockMvc.perform(post("/api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/validate",patientId,questionnaireId)
                        .header("Authorization","Bearer assistant").contentType("application/json").content("{\"lockVersion\":0}"))
                .andExpect(status().isForbidden());

        token("dentist","ROLE_DENTIST","MEDICAL_HISTORY_VALIDATE");
        mockMvc.perform(post("/api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/validate",patientId,questionnaireId)
                        .header("Authorization","Bearer dentist").contentType("application/json").content("{\"lockVersion\":0}"))
                .andExpect(status().isOk());
        verify(service).validate(eq(patientId),eq(questionnaireId),any(),any());
    }

    @Test
    void patientSelfServiceRequiresPatientRoleAndNeverAcceptsPatientId() throws Exception {
        token("staff","ROLE_ASSISTANT","MEDICAL_HISTORY_CLINICAL_READ");
        mockMvc.perform(get("/api/v1/patients/me/medical-history/questionnaires")
                        .header("Authorization","Bearer staff"))
                .andExpect(status().isForbidden());

        token("patient","ROLE_PATIENT");
        when(service.listMine(any(),eq(0),eq(20))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/patients/me/medical-history/questionnaires")
                        .header("Authorization","Bearer patient"))
                .andExpect(status().isOk());
    }

    @Test
    void secretaryCanReadStatusButCannotReadClinicalAnswers() throws Exception {
        UUID patientId=UUID.randomUUID();UUID questionnaireId=UUID.randomUUID();
        token("secretary","ROLE_SECRETARY","MEDICAL_HISTORY_STATUS_READ","MEDICAL_HISTORY_RECEIVE");
        when(service.search(isNull(),isNull(),isNull(),isNull(),isNull(),eq(0),eq(20))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/medical-history/questionnaires").header("Authorization","Bearer secretary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}",patientId,questionnaireId)
                        .header("Authorization","Bearer secretary"))
                .andExpect(status().isForbidden());
    }

    private void token(String token,String... authorities){
        when(jwtService.parseAccessToken(token)).thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(),List.of(authorities)));
    }
}
