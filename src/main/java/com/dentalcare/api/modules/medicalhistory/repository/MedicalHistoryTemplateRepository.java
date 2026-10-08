package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MedicalHistoryTemplateRepository extends JpaRepository<MedicalHistoryTemplate,UUID> {
    Optional<MedicalHistoryTemplate> findByCodeIgnoreCase(String code);
}
