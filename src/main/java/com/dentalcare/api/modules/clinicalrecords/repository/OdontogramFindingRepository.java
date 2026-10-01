package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OdontogramFindingRepository extends JpaRepository<OdontogramFinding, UUID> {

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    Page<OdontogramFinding> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findByPatient_IdAndDentitionOrderByCreatedAtAsc(UUID patientId, DentitionType dentition);

    @EntityGraph(attributePaths = {"patient", "attention", "author"})
    List<OdontogramFinding> findTop20ByPatient_IdOrderByCreatedAtDesc(UUID patientId);
}
