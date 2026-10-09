package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentRequestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AppointmentRequestMessageRepository extends JpaRepository<AppointmentRequestMessage, UUID> {
    List<AppointmentRequestMessage> findByAppointmentRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
    List<AppointmentRequestMessage> findByAppointmentRequestIdInOrderByCreatedAtAscIdAsc(Collection<UUID> requestIds);
    Optional<AppointmentRequestMessage> findByAppointmentRequestIdAndSenderAndIdempotencyKey(
            UUID requestId, String sender, UUID idempotencyKey);

    @Query("select m from AppointmentRequestMessage m where m.appointmentRequestId = :requestId " +
            "and (m.createdAt < :beforeAt or (m.createdAt = :beforeAt and m.id < :beforeId)) " +
            "order by m.createdAt desc, m.id desc")
    List<AppointmentRequestMessage> findOlderThan(@Param("requestId") UUID requestId,
            @Param("beforeAt") Instant beforeAt, @Param("beforeId") UUID beforeId, Pageable pageable);

    List<AppointmentRequestMessage> findTop20ByAppointmentRequestIdOrderByCreatedAtDescIdDesc(UUID requestId);

    @Query("select m from AppointmentRequestMessage m where m.appointmentRequestId = :requestId " +
            "order by m.createdAt desc, m.id desc")
    List<AppointmentRequestMessage> findLatest(@Param("requestId") UUID requestId, Pageable pageable);
}
