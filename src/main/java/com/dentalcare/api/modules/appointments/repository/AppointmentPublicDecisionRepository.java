package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentPublicDecision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentPublicDecisionRepository extends JpaRepository<AppointmentPublicDecision, UUID> {
    Optional<AppointmentPublicDecision> findByAppointmentRequestIdAndIdempotencyKey(
            UUID appointmentRequestId, UUID idempotencyKey);
}
