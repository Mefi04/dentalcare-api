package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.inventory.model.*;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.sterilization.dto.request.CreateSterilizationCycleRequest;
import com.dentalcare.api.modules.sterilization.mapper.*;
import com.dentalcare.api.modules.sterilization.model.*;
import com.dentalcare.api.modules.sterilization.repository.*;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
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
class SterilizationCycleServiceImplTests {
 private static final Instant NOW=Instant.parse("2026-10-01T12:00:00Z");
 @Mock SterilizationCycleRepository cycles; @Mock SterilizationProtocolRepository protocols;
 @Mock InventoryItemRepository inventory; @Mock UserRepository users;
 SterilizationCycleServiceImpl service; UUID protocolId,userId,itemId; SterilizationProtocol protocol; User user;
 @BeforeEach void setup(){
  service=new SterilizationCycleServiceImpl(cycles,protocols,inventory,users,new SterilizationCycleMapper(new SterilizationProtocolMapper()),Clock.fixed(NOW,ZoneOffset.UTC));
  protocolId=UUID.randomUUID();userId=UUID.randomUUID();itemId=UUID.randomUUID();
  protocol=new SterilizationProtocol(protocolId,"Vapor",SterilizationMethod.STEAM,null,"Pasos",true,NOW,NOW);
  user=new User(userId,"assistant","Asistente Uno","a@dental.test","1234567890123","hash",UserStatus.ACTIVE,NOW,NOW);
 }
 @Test void createsCycleWithAuthenticatedResponsibleAndExistingInstrument(){
  var item=instrument(InventoryItemType.INSTRUMENT,InventoryItemStatus.ACTIVE);
  when(protocols.findById(protocolId)).thenReturn(Optional.of(protocol));when(users.findById(userId)).thenReturn(Optional.of(user));when(inventory.findAllById(Set.of(itemId))).thenReturn(List.of(item));when(cycles.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  var result=service.create(new CreateSterilizationCycleRequest(protocolId,Set.of(itemId),"Carga clínica"),userId);
  assertThat(result.status()).isEqualTo(SterilizationCycleStatus.IN_PROGRESS);assertThat(result.responsible().id()).isEqualTo(userId);assertThat(result.instruments()).hasSize(1);assertThat(result.code()).startsWith("EST-20261001-120000-");
 }
 @Test void rejectsInactiveProtocol(){
  protocol.setActive(false);when(protocols.findById(protocolId)).thenReturn(Optional.of(protocol));
  assertThatThrownBy(()->service.create(new CreateSterilizationCycleRequest(protocolId,Set.of(itemId),"Carga"),userId)).isInstanceOf(ConflictException.class).hasMessageContaining("Inactive");
 }
 @Test void rejectsConsumable(){
  when(protocols.findById(protocolId)).thenReturn(Optional.of(protocol));when(users.findById(userId)).thenReturn(Optional.of(user));when(inventory.findAllById(Set.of(itemId))).thenReturn(List.of(instrument(InventoryItemType.CONSUMABLE,InventoryItemStatus.ACTIVE)));
  assertThatThrownBy(()->service.create(new CreateSterilizationCycleRequest(protocolId,Set.of(itemId),"Carga"),userId)).isInstanceOf(BadRequestException.class).hasMessageContaining("Only inventory instruments");
 }
 @Test void rejectsInactiveInstrument(){
  when(protocols.findById(protocolId)).thenReturn(Optional.of(protocol));when(users.findById(userId)).thenReturn(Optional.of(user));when(inventory.findAllById(Set.of(itemId))).thenReturn(List.of(instrument(InventoryItemType.INSTRUMENT,InventoryItemStatus.INACTIVE)));
  assertThatThrownBy(()->service.create(new CreateSterilizationCycleRequest(protocolId,Set.of(itemId),"Carga"),userId)).isInstanceOf(ConflictException.class).hasMessageContaining("Inactive instruments");
 }
 @Test void releasesInProgressCycle(){
  var cycle=new SterilizationCycle(UUID.randomUUID(),"EST-1",protocol,user,SterilizationCycleStatus.IN_PROGRESS,"Carga",NOW,null,Set.of(instrument(InventoryItemType.INSTRUMENT,InventoryItemStatus.ACTIVE)));
  when(cycles.findWithDetailsById(cycle.getId())).thenReturn(Optional.of(cycle));when(cycles.saveAndFlush(cycle)).thenReturn(cycle);
  var result=service.updateStatus(cycle.getId(),SterilizationCycleStatus.RELEASED);assertThat(result.status()).isEqualTo(SterilizationCycleStatus.RELEASED);assertThat(result.releasedAt()).isEqualTo(NOW);
 }
 @Test void rejectsReverseTransition(){
  var cycle=new SterilizationCycle(UUID.randomUUID(),"EST-1",protocol,user,SterilizationCycleStatus.RELEASED,"Carga",NOW,NOW,Set.of());when(cycles.findWithDetailsById(cycle.getId())).thenReturn(Optional.of(cycle));
  assertThatThrownBy(()->service.updateStatus(cycle.getId(),SterilizationCycleStatus.IN_PROGRESS)).isInstanceOf(ConflictException.class);
 }
 private InventoryItem instrument(InventoryItemType type,InventoryItemStatus status){return new InventoryItem(itemId,"INS-1","Espejo",null,type,"Clínico",status,type==InventoryItemType.CONSUMABLE?"unidad":null,type==InventoryItemType.CONSUMABLE?1:null,type==InventoryItemType.CONSUMABLE?0:null,null,type==InventoryItemType.INSTRUMENT?"Gabinete":null,type==InventoryItemType.INSTRUMENT?1:null,type==InventoryItemType.INSTRUMENT?1:null,NOW,NOW);}
}
