package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentNotificationOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentNotificationOutboxRepository extends JpaRepository<AppointmentNotificationOutboxEvent, UUID> {
    @Query(value = "select * from appointment_notification_outbox " +
            "where ((status in ('PENDING','RETRY_PENDING') and next_attempt_at <= :now) " +
            "or (status = 'PROCESSING' and processing_started_at <= :leaseExpiredAt)) " +
            "order by created_at, id for update skip locked limit 1", nativeQuery = true)
    Optional<AppointmentNotificationOutboxEvent> claimNext(@Param("now") Instant now,
                                                           @Param("leaseExpiredAt") Instant leaseExpiredAt);

    List<AppointmentNotificationOutboxEvent> findByAppointmentRequestIdOrderByCreatedAtDescIdDesc(
            UUID appointmentRequestId, Pageable pageable);
}
