package com.dentalcare.api.modules.users.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_public_profiles")
public class ProfessionalPublicProfile {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true) private User user;
    @Column(name = "professional_registration", nullable = false, length = 100) private String professionalRegistration;
    @Column(nullable = false, length = 150) private String specialty;
    @Column(nullable = false, length = 1000) private String summary;
    @Column(name = "years_experience") private Integer yearsExperience;
    @Column(length = 255) private String languages;
    @Column(name = "photo_url", length = 2048) private String photoUrl;
    @Column(name = "public_visible", nullable = false) private boolean publicVisible;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 50)
    private ProfessionalServiceCode serviceCode = ProfessionalServiceCode.GENERAL_DENTISTRY;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public ProfessionalPublicProfile() { }
    public void setId(UUID id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public ProfessionalPublicProfile(UUID id, User user, String professionalRegistration, String specialty, String summary,
                                     Integer yearsExperience, String languages, String photoUrl, boolean publicVisible,
                                     UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id=id; this.user=user; this.professionalRegistration=professionalRegistration; this.specialty=specialty;
        this.summary=summary; this.yearsExperience=yearsExperience; this.languages=languages; this.photoUrl=photoUrl;
        this.publicVisible=publicVisible; this.createdBy=createdBy; this.updatedBy=updatedBy;
        this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId(){return id;} public User getUser(){return user;} public String getProfessionalRegistration(){return professionalRegistration;}
    public String getSpecialty(){return specialty;} public String getSummary(){return summary;} public Integer getYearsExperience(){return yearsExperience;}
    public String getLanguages(){return languages;} public String getPhotoUrl(){return photoUrl;} public boolean isPublicVisible(){return publicVisible;}
    public ProfessionalServiceCode getServiceCode(){return serviceCode;}
    public void setServiceCode(ProfessionalServiceCode value){serviceCode=value;}
    public UUID getCreatedBy(){return createdBy;} public UUID getUpdatedBy(){return updatedBy;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void setProfessionalRegistration(String value){professionalRegistration=value;} public void setSpecialty(String value){specialty=value;}
    public void setSummary(String value){summary=value;} public void setYearsExperience(Integer value){yearsExperience=value;}
    public void setLanguages(String value){languages=value;} public void setPhotoUrl(String value){photoUrl=value;}
    public void setPublicVisible(boolean value){publicVisible=value;} public void setUpdatedBy(UUID value){updatedBy=value;} public void setUpdatedAt(Instant value){updatedAt=value;}
}
