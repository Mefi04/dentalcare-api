package com.dentalcare.api.modules.patients.repository;

import com.dentalcare.api.modules.patients.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByDpi(String dpi);

    @Query(value = "SELECT nextval('patient_code_seq')", nativeQuery = true)
    long nextPatientCodeSequence();

    @Query("""
            SELECT p FROM Patient p
            WHERE :search IS NULL
               OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR p.dpi LIKE CONCAT('%', :search, '%')
               OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.city) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Patient> search(@Param("search") String search, Pageable pageable);
}
