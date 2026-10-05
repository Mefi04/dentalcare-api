package com.dentalcare.api.modules.audit.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "actor_user_id", updatable = false) private UUID actorUserId;
    @Column(name = "actor_display_name", length = 120, updatable = false) private String actorDisplayName;
    @Column(name = "action_code", nullable = false, length = 64, updatable = false) private String actionCode;
    @Column(nullable = false, length = 40, updatable = false) private String module;
    @Column(name = "entity_type", length = 60, updatable = false) private String entityType;
    @Column(name = "entity_id", length = 80, updatable = false) private String entityId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12, updatable = false) private AuditResult result;
    @Column(length = 240, updatable = false) private String detail;
    protected AuditEvent() { }
    public AuditEvent(UUID id, Instant occurredAt, UUID actorUserId, String actorDisplayName, String actionCode,
                      String module, String entityType, String entityId, AuditResult result, String detail) {
        this.id=id; this.occurredAt=occurredAt; this.actorUserId=actorUserId; this.actorDisplayName=actorDisplayName;
        this.actionCode=actionCode; this.module=module; this.entityType=entityType; this.entityId=entityId;
        this.result=result; this.detail=detail;
    }
    public UUID getId(){return id;} public Instant getOccurredAt(){return occurredAt;}
    public UUID getActorUserId(){return actorUserId;} public String getActorDisplayName(){return actorDisplayName;}
    public String getActionCode(){return actionCode;} public String getModule(){return module;}
    public String getEntityType(){return entityType;} public String getEntityId(){return entityId;}
    public AuditResult getResult(){return result;} public String getDetail(){return detail;}
}
