package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.ProfessionalScheduleBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProfessionalScheduleBlockRepository extends JpaRepository<ProfessionalScheduleBlock, UUID> {
    List<ProfessionalScheduleBlock> findByProfessionalIdAndEndsAtGreaterThanOrderByStartsAtAsc(
            UUID professionalId, Instant now);
    List<ProfessionalScheduleBlock> findByProfessionalIdInAndStartsAtLessThanAndEndsAtGreaterThan(
            List<UUID> professionalIds, Instant to, Instant from);
}
