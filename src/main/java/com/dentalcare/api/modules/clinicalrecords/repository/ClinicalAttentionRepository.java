package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalAttentionRepository extends JpaRepository<ClinicalAttention, UUID> {

    @EntityGraph(attributePaths = {"patient", "professional", "appointment"})
    Page<ClinicalAttention> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "professional", "appointment"})
    Optional<ClinicalAttention> findByIdAndPatient_Id(UUID id, UUID patientId);

    @EntityGraph(attributePaths = {"patient", "professional", "appointment"})
    Optional<ClinicalAttention> findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(UUID patientId);
}
