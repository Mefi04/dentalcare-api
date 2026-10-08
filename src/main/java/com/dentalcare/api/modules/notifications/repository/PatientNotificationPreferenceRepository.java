package com.dentalcare.api.modules.notifications.repository;

import com.dentalcare.api.modules.notifications.model.PatientNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientNotificationPreferenceRepository extends JpaRepository<PatientNotificationPreference, UUID> {
    Optional<PatientNotificationPreference> findByPatient_Id(UUID patientId);
}
