package com.dentalcare.api.modules.patients.repository;

import com.dentalcare.api.modules.patients.model.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByDpi(String dpi);

    Optional<Patient> findByDpi(String dpi);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Patient p WHERE p.id = :id")
    Optional<Patient> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUser_Id(UUID userId);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Patient> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Patient> findAll(Pageable pageable);

    @Query(value = "SELECT nextval('patient_code_seq')", nativeQuery = true)
    long nextPatientCodeSequence();

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT p FROM Patient p
            WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR p.dpi LIKE CONCAT('%', :search, '%')
               OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.city) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Patient> search(@Param("search") String search, Pageable pageable);
}
