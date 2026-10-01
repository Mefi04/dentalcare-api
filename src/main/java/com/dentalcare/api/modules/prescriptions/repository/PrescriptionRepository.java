package com.dentalcare.api.modules.prescriptions.repository;

import com.dentalcare.api.modules.prescriptions.model.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    @EntityGraph(attributePaths = {"patient", "professional"})
    Page<Prescription> findByPatient_Id(UUID patientId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"patient", "professional"})
    Optional<Prescription> findById(UUID id);

    @EntityGraph(attributePaths = {"patient", "professional"})
    Optional<Prescription> findByIdAndPatient_Id(UUID id, UUID patientId);
}
