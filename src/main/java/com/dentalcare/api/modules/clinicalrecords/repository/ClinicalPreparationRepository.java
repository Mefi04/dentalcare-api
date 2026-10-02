package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalPreparation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalPreparationRepository extends JpaRepository<ClinicalPreparation, UUID> {

    @EntityGraph(attributePaths = {"patient", "attention", "preparedBy"})
    Page<ClinicalPreparation> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "preparedBy"})
    List<ClinicalPreparation> findByPatient_IdOrderByCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "preparedBy"})
    Optional<ClinicalPreparation> findByIdAndPatient_Id(UUID id, UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "preparedBy"})
    Optional<ClinicalPreparation> findFirstByPatient_IdOrderByCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "preparedBy"})
    Optional<ClinicalPreparation> findFirstByAttention_IdOrderByCreatedAtDesc(UUID attentionId);
}
