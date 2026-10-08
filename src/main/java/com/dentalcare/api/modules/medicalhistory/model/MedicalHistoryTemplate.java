package com.dentalcare.api.modules.medicalhistory.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_templates")
public class MedicalHistoryTemplate {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=80) private String code;
    @Column(nullable=false,length=150) private String name;
    @Column(nullable=false) private boolean active;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected MedicalHistoryTemplate() {}
    public MedicalHistoryTemplate(UUID id,String code,String name,Instant now){this.id=id;this.code=code;this.name=name;this.active=true;this.createdAt=now;this.updatedAt=now;}
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public boolean isActive(){return active;}
}
