package com.dentalcare.api.modules.audit.service;

import com.dentalcare.api.modules.audit.model.AuditResult;
import java.util.UUID;

public interface AuditService {
    void success(String action, String module, String entityType, UUID entityId, UUID actorId);
    void failure(String action, String module, String entityType, UUID entityId, UUID actorId);
    void record(String action, String module, String entityType, UUID entityId, UUID actorId, AuditResult result);
}
