package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryTransitionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MedicalHistoryTransitionEventRepository extends JpaRepository<MedicalHistoryTransitionEvent,UUID> {
    List<MedicalHistoryTransitionEvent> findByQuestionnaire_IdOrderByOccurredAtAsc(UUID questionnaireId);
}
