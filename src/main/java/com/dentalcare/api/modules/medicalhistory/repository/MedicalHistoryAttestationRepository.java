package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryAttestation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MedicalHistoryAttestationRepository extends JpaRepository<MedicalHistoryAttestation,UUID> {
    List<MedicalHistoryAttestation> findByQuestionnaire_IdOrderByAttestedAtAsc(UUID questionnaireId);
}
