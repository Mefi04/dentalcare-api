package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="medical_history_questionnaires")
public class MedicalHistoryQuestionnaire {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="patient_id") private Patient patient;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_version_id") private MedicalHistoryTemplateVersion templateVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private QuestionnairePurpose purpose;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private QuestionnaireSource source;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private QuestionnaireStatus status;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="base_validated_version_id") private MedicalHistoryVersion baseValidatedVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="scan_document_id") private ClinicalDocument scanDocument;
    @Column(name="delivered_at") private Instant deliveredAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delivered_by") private User deliveredBy;
    @Column(name="received_at") private Instant receivedAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="received_by") private User receivedBy;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="submitted_at") private Instant submittedAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="submitted_by") private User submittedBy;
    @Column(name="review_started_at") private Instant reviewStartedAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
    @Column(name="validated_at") private Instant validatedAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="validated_by") private User validatedBy;
    @Column(name="rejected_at") private Instant rejectedAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="rejected_by") private User rejectedBy;
    @Column(name="cancelled_at") private Instant cancelledAt; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cancelled_by") private User cancelledBy;
    @Column(name="status_reason",length=1000) private String statusReason;
    @Version @Column(name="lock_version",nullable=false) private long lockVersion;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="questionnaire",cascade=CascadeType.ALL,orphanRemoval=true) private List<MedicalHistoryQuestionnaireAnswer> answers=new ArrayList<>();
    protected MedicalHistoryQuestionnaire() {}
    public MedicalHistoryQuestionnaire(UUID id,Patient patient,MedicalHistoryTemplateVersion version,QuestionnairePurpose purpose,QuestionnaireSource source,MedicalHistoryVersion base,ClinicalDocument scan,Instant expires,Instant now){this.id=id;this.patient=patient;this.templateVersion=version;this.purpose=purpose;this.source=source;this.status=QuestionnaireStatus.DRAFT;this.baseValidatedVersion=base;this.scanDocument=scan;this.expiresAt=expires;this.createdAt=now;this.updatedAt=now;}
    public void replaceAnswers(Collection<MedicalHistoryQuestionnaireAnswer> values,Instant now){answers.clear();answers.addAll(values);updatedAt=now;}
    public void delivered(User actor,Instant now){deliveredBy=actor;deliveredAt=now;updatedAt=now;}
    public void received(User actor,ClinicalDocument scan,Instant now){receivedBy=actor;receivedAt=now;scanDocument=scan;updatedAt=now;}
    public void submit(User actor,Instant now){status=QuestionnaireStatus.SUBMITTED;submittedBy=actor;submittedAt=now;statusReason=null;updatedAt=now;}
    public void startReview(User actor,Instant now){status=QuestionnaireStatus.UNDER_REVIEW;reviewedBy=actor;reviewStartedAt=now;statusReason=null;updatedAt=now;}
    public void clarify(String reason,Instant now){status=QuestionnaireStatus.CLARIFICATION_REQUIRED;statusReason=reason;updatedAt=now;}
    public void validate(User actor,Instant now){status=QuestionnaireStatus.VALIDATED;validatedBy=actor;validatedAt=now;statusReason=null;updatedAt=now;}
    public void reject(User actor,String reason,Instant now){status=QuestionnaireStatus.REJECTED;rejectedBy=actor;rejectedAt=now;statusReason=reason;updatedAt=now;}
    public void cancel(User actor,String reason,Instant now){status=QuestionnaireStatus.CANCELLED;cancelledBy=actor;cancelledAt=now;statusReason=reason;updatedAt=now;}
    public UUID getId(){return id;} public Patient getPatient(){return patient;} public MedicalHistoryTemplateVersion getTemplateVersion(){return templateVersion;} public QuestionnairePurpose getPurpose(){return purpose;} public QuestionnaireSource getSource(){return source;} public QuestionnaireStatus getStatus(){return status;} public MedicalHistoryVersion getBaseValidatedVersion(){return baseValidatedVersion;} public ClinicalDocument getScanDocument(){return scanDocument;} public Instant getDeliveredAt(){return deliveredAt;} public Instant getReceivedAt(){return receivedAt;} public Instant getExpiresAt(){return expiresAt;} public Instant getSubmittedAt(){return submittedAt;} public Instant getReviewStartedAt(){return reviewStartedAt;} public Instant getValidatedAt(){return validatedAt;} public String getStatusReason(){return statusReason;} public long getLockVersion(){return lockVersion;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public List<MedicalHistoryQuestionnaireAnswer> getAnswers(){return answers;} public User getSubmittedBy(){return submittedBy;} public User getReviewedBy(){return reviewedBy;} public User getValidatedBy(){return validatedBy;}
}
