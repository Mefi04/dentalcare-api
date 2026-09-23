package com.dentalcare.api.modules.users.repository;

import com.dentalcare.api.modules.users.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.code = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    Optional<Role> findByCodeAndActiveTrue(String code);

    List<Role> findAllByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);
}
