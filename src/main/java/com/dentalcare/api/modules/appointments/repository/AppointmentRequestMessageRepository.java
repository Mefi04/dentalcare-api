package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentRequestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface AppointmentRequestMessageRepository extends JpaRepository<AppointmentRequestMessage, UUID> {
    List<AppointmentRequestMessage> findByAppointmentRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
    List<AppointmentRequestMessage> findByAppointmentRequestIdInOrderByCreatedAtAscIdAsc(Collection<UUID> requestIds);
}
