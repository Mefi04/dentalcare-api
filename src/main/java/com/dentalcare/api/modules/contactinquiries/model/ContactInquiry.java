package com.dentalcare.api.modules.contactinquiries.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="contact_inquiries")
public class ContactInquiry {
    @Id private UUID id;
    @Column(nullable=false,length=120,updatable=false) private String name;
    @Column(nullable=false,length=254,updatable=false) private String email;
    @Column(length=30,updatable=false) private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) private ContactInquiryReason reason;
    @Column(nullable=false,length=4000,updatable=false) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ContactInquiryStatus status;
    @Column(name="privacy_accepted",nullable=false,updatable=false) private boolean privacyAccepted;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by") private UUID updatedBy;
    protected ContactInquiry(){}
    public ContactInquiry(UUID id,String name,String email,String phone,ContactInquiryReason reason,String message,boolean privacyAccepted,Instant now){this.id=id;this.name=name;this.email=email;this.phone=phone;this.reason=reason;this.message=message;this.privacyAccepted=privacyAccepted;this.status=ContactInquiryStatus.NEW;this.createdAt=now;this.updatedAt=now;}
    public UUID getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} public String getPhone(){return phone;} public ContactInquiryReason getReason(){return reason;} public String getMessage(){return message;} public ContactInquiryStatus getStatus(){return status;} public boolean isPrivacyAccepted(){return privacyAccepted;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public UUID getUpdatedBy(){return updatedBy;}
    public void updateStatus(ContactInquiryStatus status,UUID actor,Instant now){this.status=status;this.updatedBy=actor;this.updatedAt=now;}
}
