package com.dentalcare.api.modules.audit.service;

import com.dentalcare.api.modules.audit.model.*;
import com.dentalcare.api.modules.audit.repository.AuditEventRepository;
import com.dentalcare.api.security.service.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {
    private final AuditEventRepository repository;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public AuditServiceImpl(ObjectProvider<AuditEventRepository> repository, Clock clock) { this.repository=repository.getIfAvailable(); this.clock=clock; }
    public AuditServiceImpl(AuditEventRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }

    @Override @Transactional public void success(String action, String module, String type, UUID id, UUID actor) {
        record(action,module,type,id,actor,AuditResult.SUCCESS);
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW) public void failure(String action, String module, String type, UUID id, UUID actor) {
        persist(action,module,type,id,actor,AuditResult.FAILURE);
    }
    @Override @Transactional(propagation=Propagation.REQUIRED) public void record(String action, String module, String type, UUID id, UUID actor, AuditResult result) {
        persist(action,module,type,id,actor,result);
    }
    private void persist(String action, String module, String type, UUID id, UUID actor, AuditResult result) {
        if (repository == null) throw new IllegalStateException("Audit persistence is unavailable");
        UUID effectiveActor=actor;
        if (effectiveActor==null && SecurityContextHolder.getContext().getAuthentication()!=null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthenticatedUser principal)
            effectiveActor=principal.userId();
        // Details are deliberately fixed server-side labels, never caller/request payloads.
        String detail = result == AuditResult.SUCCESS ? "Action completed" : "Action failed";
        repository.save(new AuditEvent(UUID.randomUUID(),clock.instant(),effectiveActor,null,
                safe(action,64),safe(module,40),safe(type,60),id==null?null:id.toString(),result,detail));
    }
    private String safe(String value,int max) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^A-Za-z0-9_ -]", "");
        return normalized.substring(0, Math.min(normalized.length(), max));
    }
}
