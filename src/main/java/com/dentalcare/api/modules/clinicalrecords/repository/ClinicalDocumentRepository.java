package com.dentalcare.api.modules.clinicalrecords.repository;

import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, UUID> {

    @EntityGraph(attributePaths = {"author"})
    Page<ClinicalDocument> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Page<ClinicalDocument> findByPatient_IdAndType(UUID patientId, ClinicalDocumentType type, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<ClinicalDocument> findByIdAndPatient_Id(UUID id, UUID patientId);

    Optional<ClinicalDocument> findByStorageObjectKey(String storageObjectKey);

    boolean existsByStorageObjectKey(String storageObjectKey);
}
