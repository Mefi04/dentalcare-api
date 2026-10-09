package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MedicalHistoryQuestionnaireRepository extends JpaRepository<MedicalHistoryQuestionnaire,UUID>, JpaSpecificationExecutor<MedicalHistoryQuestionnaire> {
    @EntityGraph(attributePaths={"patient","templateVersion","templateVersion.template"})
    @Query("select q from MedicalHistoryQuestionnaire q where q.id=:id") Optional<MedicalHistoryQuestionnaire> findDetailedById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"patient","templateVersion","templateVersion.template"})
    @Query("select q from MedicalHistoryQuestionnaire q where q.id=:id") Optional<MedicalHistoryQuestionnaire> findDetailedByIdForUpdate(@Param("id") UUID id);
    Page<MedicalHistoryQuestionnaire> findByPatient_Id(UUID patientId,Pageable pageable);
    Page<MedicalHistoryQuestionnaire> findByPatient_User_Id(UUID userId,Pageable pageable);
}
