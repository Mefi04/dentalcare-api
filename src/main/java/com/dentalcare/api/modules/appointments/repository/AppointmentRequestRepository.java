package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, UUID>,
        JpaSpecificationExecutor<AppointmentRequest> {

    @Override
    @EntityGraph(attributePaths = {"patient", "requestedProfessional", "proposedProfessional", "appointment"})
    Page<AppointmentRequest> findAll(org.springframework.data.jpa.domain.Specification<AppointmentRequest> spec,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "requestedProfessional", "proposedProfessional", "appointment"})
    Page<AppointmentRequest> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "requestedProfessional", "proposedProfessional", "appointment"})
    Optional<AppointmentRequest> findByIdAndPatient_Id(UUID id, UUID patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"patient", "requestedProfessional", "proposedProfessional", "appointment"})
    @Query("select r from AppointmentRequest r where r.id = :id")
    Optional<AppointmentRequest> findDetailedByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"patient", "requestedProfessional", "proposedProfessional", "appointment"})
    @Query("select r from AppointmentRequest r where r.id = :id")
    Optional<AppointmentRequest> findDetailedById(@Param("id") UUID id);

    Optional<AppointmentRequest> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByRequesterCuiAndRequestedAtAndRequestedProfessionalIsNullAndStatusIn(
            String requesterCui, java.time.Instant requestedAt, List<com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus> statuses);

    boolean existsByRequesterCuiAndRequestedAtAndRequestedProfessional_IdAndStatusIn(
            String requesterCui, java.time.Instant requestedAt, UUID professionalId,
            List<com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus> statuses);
}
