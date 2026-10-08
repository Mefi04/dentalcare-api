package com.dentalcare.api.modules.medicalhistory.model;

import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="medical_history_template_sections")
public class MedicalHistoryTemplateSection {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="template_version_id") private MedicalHistoryTemplateVersion templateVersion;
    @Column(name="section_key",nullable=false,length=80) private String sectionKey;
    @Column(nullable=false,length=180) private String title;
    @Column(length=500) private String description;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @OneToMany(mappedBy="section",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder ASC") private List<MedicalHistoryTemplateQuestion> questions=new ArrayList<>();
    protected MedicalHistoryTemplateSection() {}
    public MedicalHistoryTemplateSection(UUID id,MedicalHistoryTemplateVersion version,String key,String title,String description,int order){this.id=id;this.templateVersion=version;this.sectionKey=key;this.title=title;this.description=description;this.displayOrder=order;}
    public void addQuestion(MedicalHistoryTemplateQuestion question){questions.add(question);}
    public UUID getId(){return id;} public String getSectionKey(){return sectionKey;} public String getTitle(){return title;} public String getDescription(){return description;} public int getDisplayOrder(){return displayOrder;} public List<MedicalHistoryTemplateQuestion> getQuestions(){return questions;}
}
