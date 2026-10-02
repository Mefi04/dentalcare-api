package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OdontogramFindingRepository extends JpaRepository<OdontogramFinding, UUID> {

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    Page<OdontogramFinding> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    @Query("""
            SELECT f FROM OdontogramFinding f
            WHERE f.patient.id = :patientId
              AND (:toothCode IS NULL OR f.toothCode = :toothCode)
              AND (:dentition IS NULL OR f.dentition = :dentition)
              AND (:finding IS NULL OR f.finding = :finding)
            """)
    Page<OdontogramFinding> findByPatientWithFilters(
            @Param("patientId") UUID patientId,
            @Param("toothCode") String toothCode,
            @Param("dentition") DentitionType dentition,
            @Param("finding") ToothFinding finding,
            Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findByPatient_IdAndDentitionOrderByCreatedAtAsc(UUID patientId, DentitionType dentition);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findByPatient_IdAndDentitionOrderByCreatedAtAscIdAsc(UUID patientId, DentitionType dentition);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findTop20ByPatient_IdOrderByCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findByAttention_IdOrderByCreatedAtDesc(UUID attentionId);
}
