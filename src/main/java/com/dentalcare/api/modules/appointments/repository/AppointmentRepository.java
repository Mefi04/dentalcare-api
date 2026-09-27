package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = "professional")
    Page<Appointment> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = "professional")
    Optional<Appointment> findByIdAndPatient_Id(UUID id, UUID patientId);
}
