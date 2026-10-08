package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_transition_events")
public class MedicalHistoryTransitionEvent {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="questionnaire_id") private MedicalHistoryQuestionnaire questionnaire;
    @Enumerated(EnumType.STRING) @Column(name="from_status",length=30) private QuestionnaireStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,length=30) private QuestionnaireStatus toStatus;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id") private User actor;
    @Column(name="reason_code",length=80) private String reasonCode;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected MedicalHistoryTransitionEvent() {}
    public MedicalHistoryTransitionEvent(UUID id,MedicalHistoryQuestionnaire q,QuestionnaireStatus from,QuestionnaireStatus to,User actor,String reason,Instant at){this.id=id;this.questionnaire=q;this.fromStatus=from;this.toStatus=to;this.actor=actor;this.reasonCode=reason;this.occurredAt=at;}
    public QuestionnaireStatus getFromStatus(){return fromStatus;} public QuestionnaireStatus getToStatus(){return toStatus;} public User getActor(){return actor;} public String getReasonCode(){return reasonCode;} public Instant getOccurredAt(){return occurredAt;}
}
