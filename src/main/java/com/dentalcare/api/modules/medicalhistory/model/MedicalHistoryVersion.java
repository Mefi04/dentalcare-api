package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_versions")
public class MedicalHistoryVersion {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="patient_id") private Patient patient;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private MedicalHistoryVersionSource source;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="source_questionnaire_id") private MedicalHistoryQuestionnaire sourceQuestionnaire;
    @Column(name="source_revision_number") private Integer sourceRevisionNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="previous_version_id") private MedicalHistoryVersion previousVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="validated_by") private User validatedBy;
    @Column(name="validated_at",nullable=false) private Instant validatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String snapshot;
    @Column(nullable=false) private boolean current;
    protected MedicalHistoryVersion() {}
    public MedicalHistoryVersion(UUID id,Patient patient,int number,MedicalHistoryVersionSource source,MedicalHistoryQuestionnaire q,Integer revision,MedicalHistoryVersion previous,User validator,Instant at,String snapshot,boolean current){this.id=id;this.patient=patient;this.versionNumber=number;this.source=source;this.sourceQuestionnaire=q;this.sourceRevisionNumber=revision;this.previousVersion=previous;this.validatedBy=validator;this.validatedAt=at;this.snapshot=snapshot;this.current=current;}
    public void supersede(){current=false;}
    public UUID getId(){return id;} public Patient getPatient(){return patient;} public int getVersionNumber(){return versionNumber;} public MedicalHistoryVersionSource getSource(){return source;} public MedicalHistoryQuestionnaire getSourceQuestionnaire(){return sourceQuestionnaire;} public Integer getSourceRevisionNumber(){return sourceRevisionNumber;} public User getValidatedBy(){return validatedBy;} public Instant getValidatedAt(){return validatedAt;} public String getSnapshot(){return snapshot;} public boolean isCurrent(){return current;}
}
