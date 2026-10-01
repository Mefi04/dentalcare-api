package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.sterilization.dto.request.*;
import com.dentalcare.api.modules.sterilization.mapper.SterilizationProtocolMapper;
import com.dentalcare.api.modules.sterilization.model.*;
import com.dentalcare.api.modules.sterilization.repository.SterilizationProtocolRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class SterilizationProtocolServiceImplTests {
 private static final Instant NOW=Instant.parse("2026-10-01T12:00:00Z");
 @Mock SterilizationProtocolRepository repository;
 SterilizationProtocolServiceImpl service;
 @BeforeEach void setup(){service=new SterilizationProtocolServiceImpl(repository,new SterilizationProtocolMapper(),Clock.fixed(NOW,ZoneOffset.UTC));}
 @Test void createsActiveProtocol(){
  when(repository.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  var result=service.create(new CreateSterilizationProtocolRequest(" Vapor ",SterilizationMethod.STEAM,"Carga","Seguir protocolo"));
  assertThat(result.name()).isEqualTo("Vapor");assertThat(result.active()).isTrue();assertThat(result.createdAt()).isEqualTo(NOW);
 }
 @Test void rejectsDuplicateName(){
  when(repository.existsByNameIgnoreCase("Vapor")).thenReturn(true);
  assertThatThrownBy(()->service.create(new CreateSterilizationProtocolRequest("Vapor",SterilizationMethod.STEAM,null,"Pasos"))).isInstanceOf(ConflictException.class);
  verify(repository,never()).saveAndFlush(any());
 }
 @Test void deactivatesWithoutDeleting(){
  UUID id=UUID.randomUUID();var p=new SterilizationProtocol(id,"Vapor",SterilizationMethod.STEAM,null,"Pasos",true,NOW,NOW);
  when(repository.findById(id)).thenReturn(Optional.of(p));when(repository.saveAndFlush(p)).thenReturn(p);
  assertThat(service.updateStatus(id,false).active()).isFalse();
  verify(repository,never()).delete(any(SterilizationProtocol.class));
 }
}
