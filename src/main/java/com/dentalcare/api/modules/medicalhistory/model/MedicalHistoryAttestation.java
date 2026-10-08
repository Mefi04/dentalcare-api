package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_attestations")
public class MedicalHistoryAttestation {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="questionnaire_id") private MedicalHistoryQuestionnaire questionnaire;
    @Column(name="revision_number",nullable=false) private int revisionNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_version_id") private MedicalHistoryTemplateVersion templateVersion;
    @Enumerated(EnumType.STRING) @Column(name="attestation_type",nullable=false,length=40) private MedicalHistoryAttestationType type;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="signer_user_id") private User signerUser;
    @Column(name="signer_name",nullable=false,length=180) private String signerName;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="evidence_document_id") private ClinicalDocument evidenceDocument;
    @Column(name="attested_at",nullable=false) private Instant attestedAt;
    protected MedicalHistoryAttestation() {}
    public MedicalHistoryAttestation(UUID id,MedicalHistoryQuestionnaire q,int revision,MedicalHistoryAttestationType type,User signer,String name,ClinicalDocument evidence,Instant at){this.id=id;this.questionnaire=q;this.revisionNumber=revision;this.templateVersion=q.getTemplateVersion();this.type=type;this.signerUser=signer;this.signerName=name;this.evidenceDocument=evidence;this.attestedAt=at;}
    public MedicalHistoryAttestationType getType(){return type;} public String getSignerName(){return signerName;} public Instant getAttestedAt(){return attestedAt;} public int getRevisionNumber(){return revisionNumber;} public ClinicalDocument getEvidenceDocument(){return evidenceDocument;}
}
