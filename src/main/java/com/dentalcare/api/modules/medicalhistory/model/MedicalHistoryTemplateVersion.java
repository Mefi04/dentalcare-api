package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="medical_history_template_versions")
public class MedicalHistoryTemplateVersion {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_id") private MedicalHistoryTemplate template;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Column(nullable=false,length=180) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MedicalHistoryTemplateStatus status;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by") private User createdBy;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="published_by") private User publishedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="published_at") private Instant publishedAt;
    @OneToMany(mappedBy="templateVersion",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder ASC") private List<MedicalHistoryTemplateSection> sections=new ArrayList<>();
    protected MedicalHistoryTemplateVersion() {}
    public MedicalHistoryTemplateVersion(UUID id,MedicalHistoryTemplate template,int number,String title,User actor,Instant now){this.id=id;this.template=template;this.versionNumber=number;this.title=title;this.status=MedicalHistoryTemplateStatus.DRAFT;this.createdBy=actor;this.createdAt=now;}
    public void addSection(MedicalHistoryTemplateSection section){sections.add(section);}
    public void replaceDraftContent(String title,List<MedicalHistoryTemplateSection> replacement){
        if(status!=MedicalHistoryTemplateStatus.DRAFT)throw new IllegalStateException("Published template versions are immutable");
        this.title=title;sections.clear();sections.addAll(replacement);
    }
    public void publish(User actor,Instant now){status=MedicalHistoryTemplateStatus.PUBLISHED;publishedBy=actor;publishedAt=now;}
    public void retire(){status=MedicalHistoryTemplateStatus.RETIRED;}
    public UUID getId(){return id;} public MedicalHistoryTemplate getTemplate(){return template;} public int getVersionNumber(){return versionNumber;} public String getTitle(){return title;} public MedicalHistoryTemplateStatus getStatus(){return status;} public List<MedicalHistoryTemplateSection> getSections(){return sections;} public Instant getPublishedAt(){return publishedAt;}
}
