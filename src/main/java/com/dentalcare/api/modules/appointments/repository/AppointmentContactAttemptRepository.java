package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentContactAttempt;
import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AppointmentContactAttemptRepository extends JpaRepository<AppointmentContactAttempt, UUID> {

    boolean existsByRequest_IdAndResult(UUID requestId, AppointmentContactResult result);

    @EntityGraph(attributePaths = "actor")
    Page<AppointmentContactAttempt> findByRequest_IdOrderByAttemptedAtDesc(UUID requestId, Pageable pageable);
}
