package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.inventory.model.*;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.sterilization.dto.request.CreateSterilizationCycleRequest;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationCycleResponse;
import com.dentalcare.api.modules.sterilization.mapper.SterilizationCycleMapper;
import com.dentalcare.api.modules.sterilization.model.*;
import com.dentalcare.api.modules.sterilization.repository.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class SterilizationCycleServiceImpl implements SterilizationCycleService {
 private static final int MAX_PAGE_SIZE=100;
 private final SterilizationCycleRepository cycles; private final SterilizationProtocolRepository protocols;
 private final InventoryItemRepository inventory; private final UserRepository users; private final SterilizationCycleMapper mapper; private final Clock clock;
 @Autowired public SterilizationCycleServiceImpl(SterilizationCycleRepository cycles,SterilizationProtocolRepository protocols,InventoryItemRepository inventory,UserRepository users,SterilizationCycleMapper mapper){this(cycles,protocols,inventory,users,mapper,Clock.systemUTC());}
 SterilizationCycleServiceImpl(SterilizationCycleRepository cycles,SterilizationProtocolRepository protocols,InventoryItemRepository inventory,UserRepository users,SterilizationCycleMapper mapper,Clock clock){this.cycles=cycles;this.protocols=protocols;this.inventory=inventory;this.users=users;this.mapper=mapper;this.clock=clock;}
 @Transactional(readOnly=true) public Page<SterilizationCycleResponse> findAll(UUID protocolId,SterilizationCycleStatus status,Instant from,Instant to,int page,int size){
  validatePage(page,size);if(from!=null&&to!=null&&from.isAfter(to))throw new BadRequestException("From must not be after to");
  Specification<SterilizationCycle> spec=Specification.unrestricted();
  if(protocolId!=null)spec=spec.and((r,q,b)->b.equal(r.get("protocol").get("id"),protocolId));
  if(status!=null)spec=spec.and((r,q,b)->b.equal(r.get("status"),status));
  if(from!=null)spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("startedAt"),from));
  if(to!=null)spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("startedAt"),to));
  return cycles.findAll(spec,PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),Sort.by("startedAt").descending())).map(mapper::toResponse);
 }
 @Transactional(readOnly=true) public SterilizationCycleResponse findById(UUID id){return mapper.toResponse(find(id));}
 @Transactional public SterilizationCycleResponse create(CreateSterilizationCycleRequest r,UUID responsibleUserId){
  if(responsibleUserId==null)throw new BadRequestException("Authenticated responsible user is required");
  SterilizationProtocol protocol=protocols.findById(r.protocolId()).orElseThrow(()->new ResourceNotFoundException("Sterilization protocol not found"));
  if(!protocol.isActive())throw new ConflictException("Inactive sterilization protocols cannot be used");
  var responsible=users.findById(responsibleUserId).orElseThrow(()->new ResourceNotFoundException("Responsible user not found"));
  List<InventoryItem> found=inventory.findAllById(r.instrumentIds());
  if(found.size()!=r.instrumentIds().size())throw new ResourceNotFoundException("One or more instruments were not found");
  for(InventoryItem item:found){
   if(item.getType()!=InventoryItemType.INSTRUMENT)throw new BadRequestException("Only inventory instruments can be included in a sterilization cycle");
   if(item.getStatus()!=InventoryItemStatus.ACTIVE)throw new ConflictException("Inactive instruments cannot be included in a sterilization cycle");
  }
  Instant now=clock.instant();UUID id=UUID.randomUUID();String code="EST-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(now)+"-"+id.toString().substring(0,8).toUpperCase(Locale.ROOT);
  SterilizationCycle cycle=new SterilizationCycle(id,code,protocol,responsible,SterilizationCycleStatus.IN_PROGRESS,r.observations().trim(),now,null,new LinkedHashSet<>(found));
  return mapper.toResponse(cycles.saveAndFlush(cycle));
 }
 @Transactional public SterilizationCycleResponse updateStatus(UUID id,SterilizationCycleStatus status){
  if(status==null)throw new BadRequestException("Status is required");SterilizationCycle cycle=find(id);
  if(cycle.getStatus()==status)return mapper.toResponse(cycle);
  if(cycle.getStatus()!=SterilizationCycleStatus.IN_PROGRESS||status!=SterilizationCycleStatus.RELEASED)throw new ConflictException("Invalid sterilization cycle status transition");
  cycle.setStatus(status);cycle.setReleasedAt(clock.instant());return mapper.toResponse(cycles.saveAndFlush(cycle));
 }
 private SterilizationCycle find(UUID id){if(id==null)throw new BadRequestException("Cycle id is required");return cycles.findWithDetailsById(id).orElseThrow(()->new ResourceNotFoundException("Sterilization cycle not found"));}
 private void validatePage(int p,int s){if(p<0)throw new BadRequestException("Page must not be negative");if(s<1)throw new BadRequestException("Size must be at least 1");}
}
