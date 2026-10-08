package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryAnswerRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MedicalHistoryAnswerRevisionRepository extends JpaRepository<MedicalHistoryAnswerRevision,UUID> {
    Optional<MedicalHistoryAnswerRevision> findFirstByQuestionnaire_IdOrderByRevisionNumberDesc(UUID questionnaireId);
}
