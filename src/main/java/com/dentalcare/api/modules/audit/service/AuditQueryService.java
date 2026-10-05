package com.dentalcare.api.modules.audit.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.audit.dto.AuditEventResponse;
import com.dentalcare.api.modules.audit.model.AuditEvent;
import com.dentalcare.api.modules.audit.model.AuditResult;
import com.dentalcare.api.modules.audit.repository.AuditEventRepository;
import org.springframework.data.domain.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditQueryService {
    private static final int MAX_SIZE=100;
    private final AuditEventRepository repository;
    @org.springframework.beans.factory.annotation.Autowired
    public AuditQueryService(ObjectProvider<AuditEventRepository> repository){this.repository=repository.getIfAvailable();}
    public AuditQueryService(AuditEventRepository repository){this.repository=repository;}
    @Transactional(readOnly=true)
    public Page<AuditEventResponse> search(Instant from, Instant to, UUID actor, String module, String action,
                                            AuditResult result, String q, int page, int size) {
        if (repository == null) throw new IllegalStateException("Audit persistence is unavailable");
        if(page<0 || size<1 || size>MAX_SIZE) throw new BadRequestException("Invalid pagination parameters");
        if(from!=null && to!=null && from.isAfter(to)) throw new BadRequestException("From date must not be after to date");
        Specification<AuditEvent> spec=Specification.unrestricted();
        if(from!=null) spec=spec.and((r,c,b)->b.greaterThanOrEqualTo(r.get("occurredAt"),from));
        if(to!=null) spec=spec.and((r,c,b)->b.lessThanOrEqualTo(r.get("occurredAt"),to));
        if(actor!=null) spec=spec.and((r,c,b)->b.equal(r.get("actorUserId"),actor));
        if(module!=null && !module.isBlank()) spec=spec.and((r,c,b)->b.equal(b.upper(r.get("module")),module.toUpperCase()));
        if(action!=null && !action.isBlank()) spec=spec.and((r,c,b)->b.equal(r.get("actionCode"),action));
        if(result!=null) spec=spec.and((r,c,b)->b.equal(r.get("result"),result));
        if(q!=null && !q.isBlank()) {
            if(q.length()>80) throw new BadRequestException("Search query is too long");
            String needle="%"+q.toLowerCase().replace("%","\\%").replace("_","\\_")+"%";
            spec=spec.and((r,c,b)->b.or(b.like(b.lower(r.get("actionCode")),needle,'\\'), b.like(b.lower(r.get("module")),needle,'\\'), b.like(b.lower(r.get("detail")),needle,'\\')));
        }
        Pageable pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("occurredAt"),Sort.Order.desc("id")));
        return repository.findAll(spec,pageable).map(e->new AuditEventResponse(e.getId(),e.getOccurredAt(),e.getActorUserId(),e.getActorDisplayName(),e.getActionCode(),e.getModule(),e.getEntityType(),e.getEntityId(),e.getResult(),e.getDetail()));
    }
}
