package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClinicalDiagnosisRepository extends JpaRepository<ClinicalDiagnosis, UUID> {

    @EntityGraph(attributePaths = {"patient", "attention", "treatmentPlan", "author"})
    Page<ClinicalDiagnosis> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "treatmentPlan", "author"})
    Page<ClinicalDiagnosis> findByPatient_IdAndType(UUID patientId, DiagnosisType type, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "treatmentPlan", "author"})
    List<ClinicalDiagnosis> findTop10ByPatient_IdOrderByCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "treatmentPlan", "author"})
    List<ClinicalDiagnosis> findByAttention_IdOrderByCreatedAtDesc(UUID attentionId);
}
