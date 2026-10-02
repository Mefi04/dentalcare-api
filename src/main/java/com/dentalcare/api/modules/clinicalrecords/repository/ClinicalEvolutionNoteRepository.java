package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalEvolutionNoteRepository extends JpaRepository<ClinicalEvolutionNote, UUID> {

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    Page<ClinicalEvolutionNote> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    Optional<ClinicalEvolutionNote> findFirstByPatient_IdOrderByConsultationDateDescCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<ClinicalEvolutionNote> findByAttention_IdOrderByConsultationDateDescCreatedAtDesc(UUID attentionId);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    Optional<ClinicalEvolutionNote> findByIdAndPatient_Id(UUID id, UUID patientId);
}
