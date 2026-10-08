package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_review_notes")
public class MedicalHistoryReviewNote {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="questionnaire_id") private MedicalHistoryQuestionnaire questionnaire;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private User author;
    @Enumerated(EnumType.STRING) @Column(name="note_type",nullable=false,length=30) private MedicalHistoryReviewNoteType noteType;
    @Column(name="note_text",nullable=false,length=2000) private String noteText;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected MedicalHistoryReviewNote() {}
    public MedicalHistoryReviewNote(UUID id,MedicalHistoryQuestionnaire q,User author,MedicalHistoryReviewNoteType type,String text,Instant at){this.id=id;this.questionnaire=q;this.author=author;this.noteType=type;this.noteText=text;this.createdAt=at;}
    public UUID getId(){return id;} public User getAuthor(){return author;} public MedicalHistoryReviewNoteType getNoteType(){return noteType;} public String getNoteText(){return noteText;} public Instant getCreatedAt(){return createdAt;}
}
