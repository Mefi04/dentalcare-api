package com.dentalcare.api.modules.medicalhistory.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_questionnaire_answers")
public class MedicalHistoryQuestionnaireAnswer {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="questionnaire_id") private MedicalHistoryQuestionnaire questionnaire;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="question_id") private MedicalHistoryTemplateQuestion question;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="answer_value",nullable=false,columnDefinition="jsonb") private String answerValue;
    @Column(length=2000) private String note;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected MedicalHistoryQuestionnaireAnswer() {}
    public MedicalHistoryQuestionnaireAnswer(UUID id,MedicalHistoryQuestionnaire questionnaire,MedicalHistoryTemplateQuestion question,String value,String note,Instant now){this.id=id;this.questionnaire=questionnaire;this.question=question;this.answerValue=value;this.note=note;this.createdAt=now;this.updatedAt=now;}
    public UUID getId(){return id;} public MedicalHistoryTemplateQuestion getQuestion(){return question;} public String getAnswerValue(){return answerValue;} public String getNote(){return note;}
}
