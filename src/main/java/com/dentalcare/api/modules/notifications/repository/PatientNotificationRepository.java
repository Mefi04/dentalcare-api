package com.dentalcare.api.modules.notifications.repository;

import com.dentalcare.api.modules.notifications.model.PatientNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PatientNotificationRepository extends JpaRepository<PatientNotification, UUID> {
    Page<PatientNotification> findByPatient_Id(UUID patientId, Pageable pageable);
    Optional<PatientNotification> findByIdAndPatient_Id(UUID id, UUID patientId);

    @Modifying(clearAutomatically = true)
    @Query("update PatientNotification n set n.readAt = :readAt where n.patient.id = :patientId and n.readAt is null")
    int markAllUnreadAsRead(@Param("patientId") UUID patientId, @Param("readAt") Instant readAt);
}
