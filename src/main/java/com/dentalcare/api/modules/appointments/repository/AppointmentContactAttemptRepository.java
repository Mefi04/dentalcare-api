package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentContactAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppointmentContactAttemptRepository extends JpaRepository<AppointmentContactAttempt, UUID> {
    @EntityGraph(attributePaths = "actor")
    Page<AppointmentContactAttempt> findByAppointmentRequest_Id(UUID requestId, Pageable pageable);
}
