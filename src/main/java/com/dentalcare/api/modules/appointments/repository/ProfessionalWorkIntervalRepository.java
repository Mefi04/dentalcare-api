package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.ProfessionalWorkInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProfessionalWorkIntervalRepository extends JpaRepository<ProfessionalWorkInterval, UUID> {
    List<ProfessionalWorkInterval> findByProfessionalIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(UUID professionalId);
    List<ProfessionalWorkInterval> findByProfessionalIdInAndDayOfWeekAndActiveTrue(
            List<UUID> professionalIds, int dayOfWeek);
}
