package com.dentalcare.api.modules.medicalhistory.model;

import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="medical_history_answer_revisions")
public class MedicalHistoryAnswerRevision {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="questionnaire_id") private MedicalHistoryQuestionnaire questionnaire;
    @Column(name="revision_number",nullable=false) private int revisionNumber;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="answers_snapshot",nullable=false,columnDefinition="jsonb") private String answersSnapshot;
    @Column(name="submitted_at",nullable=false) private Instant submittedAt;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="submitted_by") private User submittedBy;
    protected MedicalHistoryAnswerRevision() {}
    public MedicalHistoryAnswerRevision(UUID id,MedicalHistoryQuestionnaire q,int number,String snapshot,Instant at,User by){this.id=id;this.questionnaire=q;this.revisionNumber=number;this.answersSnapshot=snapshot;this.submittedAt=at;this.submittedBy=by;}
    public int getRevisionNumber(){return revisionNumber;} public String getAnswersSnapshot(){return answersSnapshot;}
}
