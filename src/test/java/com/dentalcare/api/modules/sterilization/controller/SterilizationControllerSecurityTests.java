package com.dentalcare.api.modules.sterilization.controller;
import com.dentalcare.api.config.*;
import com.dentalcare.api.exception.GlobalExceptionHandler;
import com.dentalcare.api.modules.sterilization.dto.request.CreateSterilizationCycleRequest;
import com.dentalcare.api.modules.sterilization.dto.response.*;
import com.dentalcare.api.modules.sterilization.model.*;
import com.dentalcare.api.modules.sterilization.service.*;
import com.dentalcare.api.security.filter.JwtAuthenticationFilter;
import com.dentalcare.api.security.handler.*;
import com.dentalcare.api.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={SterilizationProtocolController.class,SterilizationCycleController.class},properties="FRONTEND_URL=http://localhost:3000")
@Import({SecurityConfig.class,CorsConfig.class,JwtAuthenticationFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,GlobalExceptionHandler.class})
class SterilizationControllerSecurityTests {
 @Autowired MockMvc mvc; @MockitoBean SterilizationProtocolService protocols; @MockitoBean SterilizationCycleService cycles; @MockitoBean JwtService jwt;
 @Test void listRejectsUnauthenticatedWith401()throws Exception{mvc.perform(get("/api/v1/sterilization/protocols")).andExpect(status().isUnauthorized());}
 @Test void listRejectsCallerWithoutReadWith403()throws Exception{when(jwt.parseAccessToken("token")).thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(),List.of("PATIENT_READ")));mvc.perform(get("/api/v1/sterilization/protocols").header("Authorization","Bearer token")).andExpect(status().isForbidden());}
 @Test void createCycleRejectsReadOnlyCallerWith403()throws Exception{when(jwt.parseAccessToken("token")).thenReturn(new JwtService.AccessTokenClaims(UUID.randomUUID(),List.of("STERILIZATION_READ")));mvc.perform(post("/api/v1/sterilization/cycles").header("Authorization","Bearer token").contentType(MediaType.APPLICATION_JSON).content("{\"protocolId\":\""+UUID.randomUUID()+"\",\"instrumentIds\":[\""+UUID.randomUUID()+"\"],\"observations\":\"Carga\"}")).andExpect(status().isForbidden());}
 @Test void createCycleUsesAuthenticatedUser()throws Exception{
  UUID userId=UUID.randomUUID(),cycleId=UUID.randomUUID(),protocolId=UUID.randomUUID(),instrumentId=UUID.randomUUID();Instant now=Instant.parse("2026-10-01T12:00:00Z");
  when(jwt.parseAccessToken("token")).thenReturn(new JwtService.AccessTokenClaims(userId,List.of("STERILIZATION_WRITE")));
  var protocol=new SterilizationProtocolResponse(protocolId,"Vapor",SterilizationMethod.STEAM,null,"Pasos",true,now,now);
  when(cycles.create(any(CreateSterilizationCycleRequest.class),eq(userId))).thenReturn(new SterilizationCycleResponse(cycleId,"EST-1",protocol,new SterilizationResponsibleResponse(userId,"Asistente"),SterilizationCycleStatus.IN_PROGRESS,"Carga",now,null,List.of(new SterilizationInstrumentResponse(instrumentId,"INS-1","Espejo"))));
  mvc.perform(post("/api/v1/sterilization/cycles").header("Authorization","Bearer token").contentType(MediaType.APPLICATION_JSON).content("{\"protocolId\":\""+protocolId+"\",\"instrumentIds\":[\""+instrumentId+"\"],\"observations\":\"Carga\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.responsible.id").value(userId.toString()));
  verify(cycles).create(any(CreateSterilizationCycleRequest.class),eq(userId));
 }
}
