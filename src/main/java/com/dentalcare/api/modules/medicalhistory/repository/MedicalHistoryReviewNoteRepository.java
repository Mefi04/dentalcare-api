package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryReviewNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MedicalHistoryReviewNoteRepository extends JpaRepository<MedicalHistoryReviewNote,UUID> {
    List<MedicalHistoryReviewNote> findByQuestionnaire_IdOrderByCreatedAtAsc(UUID questionnaireId);
}
