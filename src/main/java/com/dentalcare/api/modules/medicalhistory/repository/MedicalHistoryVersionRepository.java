package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MedicalHistoryVersionRepository extends JpaRepository<MedicalHistoryVersion,UUID> {
    Optional<MedicalHistoryVersion> findByPatient_IdAndCurrentTrue(UUID patientId);
    Optional<MedicalHistoryVersion> findByIdAndPatient_Id(UUID id,UUID patientId);
    Page<MedicalHistoryVersion> findByPatient_Id(UUID patientId,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from MedicalHistoryVersion v where v.patient.id=:patientId and v.current=true") Optional<MedicalHistoryVersion> findCurrentForUpdate(@Param("patientId") UUID patientId);
    long countByPatient_Id(UUID patientId);
}
