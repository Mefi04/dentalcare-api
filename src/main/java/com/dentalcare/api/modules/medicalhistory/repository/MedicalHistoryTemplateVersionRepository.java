package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MedicalHistoryTemplateVersionRepository extends JpaRepository<MedicalHistoryTemplateVersion,UUID> {
    @EntityGraph(attributePaths={"template","sections"})
    @Query("select v from MedicalHistoryTemplateVersion v where v.id=:id") Optional<MedicalHistoryTemplateVersion> findDetailedById(@Param("id") UUID id);
    @EntityGraph(attributePaths={"template","sections"})
    Optional<MedicalHistoryTemplateVersion> findFirstByStatusOrderByPublishedAtDesc(MedicalHistoryTemplateStatus status);
    int countByTemplate_Id(UUID templateId);
    List<MedicalHistoryTemplateVersion> findByTemplate_IdAndStatus(UUID templateId,MedicalHistoryTemplateStatus status);
}
