package com.dentalcare.api.modules.auth.repository;

import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.users.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session where session.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshSession> findAllByFamilyId(UUID familyId);

    List<RefreshSession> findAllByUser(User user);

    List<RefreshSession> findAllByUserId(UUID userId);
}
