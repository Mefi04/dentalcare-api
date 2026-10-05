package com.dentalcare.api.modules.users.repository;

import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfessionalPublicProfileRepository extends JpaRepository<ProfessionalPublicProfile, UUID> {
    Optional<ProfessionalPublicProfile> findByUser_Id(UUID userId);

    @Query("""
            SELECT DISTINCT p FROM ProfessionalPublicProfile p JOIN FETCH p.user u JOIN u.roles r
            WHERE p.publicVisible = true AND u.status = com.dentalcare.api.modules.users.model.UserStatus.ACTIVE
              AND r.code = 'DENTIST' AND r.active = true ORDER BY LOWER(u.fullName), p.id
            """)
    List<ProfessionalPublicProfile> findAllPubliclyVisible();

    @Query("""
            SELECT DISTINCT p FROM ProfessionalPublicProfile p JOIN FETCH p.user u JOIN u.roles r
            WHERE p.id = :id AND p.publicVisible = true AND u.status = com.dentalcare.api.modules.users.model.UserStatus.ACTIVE
              AND r.code = 'DENTIST' AND r.active = true
            """)
    Optional<ProfessionalPublicProfile> findPubliclyVisibleById(@Param("id") UUID id);
}
