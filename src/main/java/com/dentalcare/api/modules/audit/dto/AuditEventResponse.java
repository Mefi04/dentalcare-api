package com.dentalcare.api.modules.audit.dto;

import com.dentalcare.api.modules.audit.model.AuditResult;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, Instant occurredAt, UUID actorUserId, String actorDisplayName,
                                 String actionCode, String module, String entityType, String entityId,
                                 AuditResult result, String detail) { }
