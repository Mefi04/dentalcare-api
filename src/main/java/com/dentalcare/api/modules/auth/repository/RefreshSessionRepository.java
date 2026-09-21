package com.dentalcare.api.modules.auth.repository;

import com.dentalcare.api.modules.auth.model.RefreshSession;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findAllByFamilyId(UUID familyId);

    List<RefreshSession> findAllByUser(User user);

    List<RefreshSession> findAllByUserId(UUID userId);
}
