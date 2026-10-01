package com.dentalcare.api.modules.auth.repository;

import com.dentalcare.api.modules.auth.model.PasswordRecoveryToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecoveryToken> findFirstByUser_IdOrderByRequestedAtDesc(UUID userId);

    List<PasswordRecoveryToken> findAllByUser_IdAndUsedAtIsNullAndRevokedAtIsNull(UUID userId);
}
