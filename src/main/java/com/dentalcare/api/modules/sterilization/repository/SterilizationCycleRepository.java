package com.dentalcare.api.modules.sterilization.repository;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycle;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SterilizationCycleRepository extends JpaRepository<SterilizationCycle,UUID>, JpaSpecificationExecutor<SterilizationCycle> {
 @EntityGraph(attributePaths={"protocol","responsible","instruments"})
 @Query("select c from SterilizationCycle c where c.id = :id")
 Optional<SterilizationCycle> findWithDetailsById(@org.springframework.data.repository.query.Param("id") UUID id);
}
