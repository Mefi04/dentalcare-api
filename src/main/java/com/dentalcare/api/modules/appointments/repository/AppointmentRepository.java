package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    boolean existsByProfessional_IdAndScheduledAtAndStatus(
            UUID professionalId, Instant scheduledAt, AppointmentStatus status);

    boolean existsByProfessional_IdAndScheduledAtAndStatusAndIdNot(
            UUID professionalId, Instant scheduledAt, AppointmentStatus status, UUID id);

    List<Appointment> findByProfessional_IdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusOrderByScheduledAtAsc(
            UUID professionalId, Instant from, Instant to, AppointmentStatus status);

    @EntityGraph(attributePaths = "professional")
    Page<Appointment> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = "professional")
    Optional<Appointment> findByIdAndPatient_Id(UUID id, UUID patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "professional")
    @Query("SELECT a FROM Appointment a WHERE a.id = :id AND a.patient.id = :patientId")
    Optional<Appointment> findByIdAndPatient_IdForUpdate(
            @Param("id") UUID id, @Param("patientId") UUID patientId);
}
