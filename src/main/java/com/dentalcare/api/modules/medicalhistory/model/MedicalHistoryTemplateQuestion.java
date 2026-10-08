package com.dentalcare.api.modules.medicalhistory.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="medical_history_template_questions")
public class MedicalHistoryTemplateQuestion {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="section_id") private MedicalHistoryTemplateSection section;
    @Column(name="question_key",nullable=false,length=100) private String questionKey;
    @Column(nullable=false,length=500) private String prompt;
    @Enumerated(EnumType.STRING) @Column(name="answer_type",nullable=false,length=30) private MedicalHistoryAnswerType answerType;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean required;
    @Column(name="notes_allowed",nullable=false) private boolean notesAllowed;
    @Column(name="max_length") private Integer maxLength;
    @Column(name="min_value",precision=15,scale=4) private BigDecimal minValue;
    @Column(name="max_value",precision=15,scale=4) private BigDecimal maxValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="choices_json",columnDefinition="jsonb") private String choicesJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="conditional_note_json",columnDefinition="jsonb") private String conditionalNoteJson;
    protected MedicalHistoryTemplateQuestion() {}
    public MedicalHistoryTemplateQuestion(UUID id,MedicalHistoryTemplateSection section,String key,String prompt,MedicalHistoryAnswerType type,int order,boolean required,boolean notesAllowed,Integer maxLength,BigDecimal min,BigDecimal max,String choices,String conditional){this.id=id;this.section=section;this.questionKey=key;this.prompt=prompt;this.answerType=type;this.displayOrder=order;this.required=required;this.notesAllowed=notesAllowed;this.maxLength=maxLength;this.minValue=min;this.maxValue=max;this.choicesJson=choices;this.conditionalNoteJson=conditional;}
    public UUID getId(){return id;} public String getQuestionKey(){return questionKey;} public String getPrompt(){return prompt;} public MedicalHistoryAnswerType getAnswerType(){return answerType;} public int getDisplayOrder(){return displayOrder;} public boolean isRequired(){return required;} public boolean isNotesAllowed(){return notesAllowed;} public Integer getMaxLength(){return maxLength;} public BigDecimal getMinValue(){return minValue;} public BigDecimal getMaxValue(){return maxValue;} public String getChoicesJson(){return choicesJson;} public String getConditionalNoteJson(){return conditionalNoteJson;}
}
