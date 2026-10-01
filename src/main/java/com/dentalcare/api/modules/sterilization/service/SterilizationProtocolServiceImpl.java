package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.sterilization.dto.request.*;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationProtocolResponse;
import com.dentalcare.api.modules.sterilization.mapper.SterilizationProtocolMapper;
import com.dentalcare.api.modules.sterilization.model.SterilizationProtocol;
import com.dentalcare.api.modules.sterilization.repository.SterilizationProtocolRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
@Service
public class SterilizationProtocolServiceImpl implements SterilizationProtocolService {
 private static final int MAX_PAGE_SIZE=100;
 private final SterilizationProtocolRepository repository; private final SterilizationProtocolMapper mapper; private final Clock clock;
 @Autowired public SterilizationProtocolServiceImpl(SterilizationProtocolRepository repository,SterilizationProtocolMapper mapper){this(repository,mapper,Clock.systemUTC());}
 SterilizationProtocolServiceImpl(SterilizationProtocolRepository repository,SterilizationProtocolMapper mapper,Clock clock){this.repository=repository;this.mapper=mapper;this.clock=clock;}
 @Transactional(readOnly=true) public Page<SterilizationProtocolResponse> findAll(String search,Boolean active,int page,int size){
  validatePage(page,size); Specification<SterilizationProtocol> spec=Specification.unrestricted();
  if(search!=null&&!search.isBlank()){String term="%"+search.trim().toLowerCase()+"%";spec=spec.and((r,q,b)->b.or(b.like(b.lower(r.get("name")),term),b.like(b.lower(r.get("description")),term)));}
  if(active!=null) spec=spec.and((r,q,b)->b.equal(r.get("active"),active));
  return repository.findAll(spec,PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),Sort.by("name").ascending())).map(mapper::toResponse);
 }
 @Transactional(readOnly=true) public SterilizationProtocolResponse findById(UUID id){return mapper.toResponse(find(id));}
 @Transactional public SterilizationProtocolResponse create(CreateSterilizationProtocolRequest r){
  String name=r.name().trim(); if(repository.existsByNameIgnoreCase(name))throw new ConflictException("A sterilization protocol with this name already exists");
  Instant now=clock.instant(); return save(new SterilizationProtocol(UUID.randomUUID(),name,r.method(),trimNullable(r.description()),r.instructions().trim(),true,now,now));
 }
 @Transactional public SterilizationProtocolResponse update(UUID id,UpdateSterilizationProtocolRequest r){
  SterilizationProtocol p=find(id);String name=r.name().trim();if(repository.existsByNameIgnoreCaseAndIdNot(name,id))throw new ConflictException("A sterilization protocol with this name already exists");
  p.setName(name);p.setMethod(r.method());p.setDescription(trimNullable(r.description()));p.setInstructions(r.instructions().trim());p.setUpdatedAt(clock.instant());return save(p);
 }
 @Transactional public SterilizationProtocolResponse updateStatus(UUID id,boolean active){SterilizationProtocol p=find(id);p.setActive(active);p.setUpdatedAt(clock.instant());return save(p);}
 private SterilizationProtocol find(UUID id){if(id==null)throw new BadRequestException("Protocol id is required");return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Sterilization protocol not found"));}
 private SterilizationProtocolResponse save(SterilizationProtocol p){try{return mapper.toResponse(repository.saveAndFlush(p));}catch(DataIntegrityViolationException e){throw new ConflictException("Sterilization protocol data conflicts with an existing record");}}
 private void validatePage(int p,int s){if(p<0)throw new BadRequestException("Page must not be negative");if(s<1)throw new BadRequestException("Size must be at least 1");}
 private String trimNullable(String v){return v==null?null:v.trim();}
}
