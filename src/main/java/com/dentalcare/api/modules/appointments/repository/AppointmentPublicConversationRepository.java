package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentPublicConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentPublicConversationRepository extends JpaRepository<AppointmentPublicConversation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AppointmentPublicConversation c where c.appointmentRequestId = :id")
    Optional<AppointmentPublicConversation> findForUpdate(@Param("id") UUID requestId);
    Optional<AppointmentPublicConversation> findByConversationTokenHash(String tokenHash);
}
