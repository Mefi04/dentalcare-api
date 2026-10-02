package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.WaitingRoomEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WaitingRoomRepository extends JpaRepository<WaitingRoomEntry, UUID>,
        JpaSpecificationExecutor<WaitingRoomEntry> {

    @Override
    @EntityGraph(attributePaths = {"appointment.patient", "appointment.professional", "checkedInBy", "lastUpdatedBy"})
    Page<WaitingRoomEntry> findAll(org.springframework.data.jpa.domain.Specification<WaitingRoomEntry> spec,
                                   Pageable pageable);

    boolean existsByAppointment_Id(UUID appointmentId);

    @EntityGraph(attributePaths = {"appointment.patient", "appointment.professional", "checkedInBy", "lastUpdatedBy"})
    Optional<WaitingRoomEntry> findByAppointment_Id(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"appointment.patient", "appointment.professional", "checkedInBy", "lastUpdatedBy"})
    @Query("select w from WaitingRoomEntry w where w.appointment.id = :appointmentId")
    Optional<WaitingRoomEntry> findByAppointmentIdForUpdate(@Param("appointmentId") UUID appointmentId);
}
