package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.settings.dto.request.CreateProcedureCatalogItemRequest;
import com.dentalcare.api.modules.settings.dto.request.UpdateProcedureCatalogItemRequest;
import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import com.dentalcare.api.modules.settings.mapper.ProcedureCatalogItemMapper;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItem;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import com.dentalcare.api.modules.settings.repository.ProcedureCatalogItemRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProcedureCatalogServiceImpl implements ProcedureCatalogService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;
    private static final int MAX_PAGE_SIZE=100;
    private static final Sort DEFAULT_SORT=Sort.by(Sort.Order.asc("name"),Sort.Order.asc("id"));
    private final ProcedureCatalogItemRepository repository;
    private final UserRepository userRepository;
    private final ProcedureCatalogItemMapper mapper;
    private final Clock clock;
    public ProcedureCatalogServiceImpl(ProcedureCatalogItemRepository repository, UserRepository userRepository,
                                       ProcedureCatalogItemMapper mapper, Clock clock) {
        this.repository=repository; this.userRepository=userRepository; this.mapper=mapper; this.clock=clock;
    }

    @Override @Transactional(readOnly=true)
    public Page<ProcedureCatalogItemResponse> findAll(String search,String category,ProcedureCatalogItemStatus status,int page,int size){
        validatePage(page,size); Specification<ProcedureCatalogItem> spec=Specification.unrestricted();
        if(search!=null&&!search.isBlank()){String term="%"+search.trim().toLowerCase(Locale.ROOT)+"%";
            spec=spec.and((root,q,cb)->cb.or(cb.like(cb.lower(root.get("code")),term),cb.like(cb.lower(root.get("name")),term)));}
        if(category!=null&&!category.isBlank()){String value=category.trim().toLowerCase(Locale.ROOT);
            spec=spec.and((root,q,cb)->cb.equal(cb.lower(root.get("category")),value));}
        if(status!=null) spec=spec.and((root,q,cb)->cb.equal(root.get("status"),status));
        return repository.findAll(spec,PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),DEFAULT_SORT)).map(mapper::toResponse);
    }
    @Override @Transactional(readOnly=true)
    public List<ProcedureCatalogItemResponse> findActive(){
        return repository.findByStatusOrderByNameAscIdAsc(ProcedureCatalogItemStatus.ACTIVE).stream().map(mapper::toResponse).toList();
    }
    @Override @Transactional(readOnly=true)
    public ProcedureCatalogItemResponse findById(UUID id){return mapper.toResponse(find(id));}
    @Override @Transactional
    public ProcedureCatalogItemResponse create(CreateProcedureCatalogItemRequest request,UUID actorId){
        requireActor(actorId); String code=normalizeCode(request.code()); String name=required(request.name());
        ensureUnique(code,name,null); var now=clock.instant();
        var item=new ProcedureCatalogItem(UUID.randomUUID(),code,name,required(request.category()),request.durationMinutes(),
                request.basePrice(),ProcedureCatalogItemStatus.ACTIVE,actorId,actorId,now,now);
        var response=save(item); if(auditService!=null)auditService.success(AuditActions.SETTINGS_CATALOG_CHANGED,"SETTINGS","ProcedureCatalogItem",item.getId(),actorId); return response;
    }
    @Override @Transactional
    public ProcedureCatalogItemResponse update(UUID id,UpdateProcedureCatalogItemRequest request,UUID actorId){
        requireActor(actorId); ProcedureCatalogItem item=find(id); String code=normalizeCode(request.code()); String name=required(request.name());
        ensureUnique(code,name,id); item.setCode(code); item.setName(name); item.setCategory(required(request.category()));
        item.setDurationMinutes(request.durationMinutes()); item.setBasePrice(request.basePrice()); item.setUpdatedBy(actorId);
        item.setUpdatedAt(clock.instant()); var response=save(item); if(auditService!=null)auditService.success(AuditActions.SETTINGS_CATALOG_CHANGED,"SETTINGS","ProcedureCatalogItem",id,actorId); return response;
    }
    @Override @Transactional
    public ProcedureCatalogItemResponse updateStatus(UUID id,ProcedureCatalogItemStatus status,UUID actorId){
        requireActor(actorId); if(status==null)throw new BadRequestException("Status is required"); ProcedureCatalogItem item=find(id);
        item.setStatus(status); item.setUpdatedBy(actorId); item.setUpdatedAt(clock.instant()); var response=save(item); if(auditService!=null)auditService.success(AuditActions.SETTINGS_CATALOG_CHANGED,"SETTINGS","ProcedureCatalogItem",id,actorId); return response;
    }
    private ProcedureCatalogItem find(UUID id){if(id==null)throw new BadRequestException("Procedure catalog item id is required");
        return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Procedure catalog item not found"));}
    private void ensureUnique(String code,String name,UUID excluded){
        boolean codeExists=excluded==null?repository.existsByCodeIgnoreCase(code):repository.existsByCodeIgnoreCaseAndIdNot(code,excluded);
        if(codeExists)throw new ConflictException("A procedure catalog item with this code already exists");
        boolean nameExists=excluded==null?repository.existsByNameIgnoreCase(name):repository.existsByNameIgnoreCaseAndIdNot(name,excluded);
        if(nameExists)throw new ConflictException("A procedure catalog item with this name already exists");
    }
    private ProcedureCatalogItemResponse save(ProcedureCatalogItem item){try{return mapper.toResponse(repository.saveAndFlush(item));}
        catch(DataIntegrityViolationException ex){throw new ConflictException("Procedure catalog item data conflicts with an existing record");}}
    private void requireActor(UUID actorId){if(actorId==null)throw new BadRequestException("Authenticated actor is required");
        if(!userRepository.existsById(actorId))throw new BadRequestException("Authenticated actor is invalid");}
    private void validatePage(int page,int size){if(page<0)throw new BadRequestException("Page must not be negative"); if(size<1)throw new BadRequestException("Size must be at least 1");}
    private String normalizeCode(String value){return required(value).toUpperCase(Locale.ROOT);}
    private String required(String value){return value.trim();}
}
