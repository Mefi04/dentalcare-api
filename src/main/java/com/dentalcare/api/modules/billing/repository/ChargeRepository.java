package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.Charge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    List<Charge> findByPatient_IdOrderByCreatedAtAscIdAsc(UUID patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Charge c WHERE c.id = :id AND c.patient.id = :patientId")
    Optional<Charge> findByIdAndPatientIdForUpdate(@Param("id") UUID id, @Param("patientId") UUID patientId);
}
