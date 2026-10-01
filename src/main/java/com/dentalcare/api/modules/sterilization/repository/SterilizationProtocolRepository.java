package com.dentalcare.api.modules.sterilization.repository;
import com.dentalcare.api.modules.sterilization.model.SterilizationProtocol;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface SterilizationProtocolRepository extends JpaRepository<SterilizationProtocol,UUID>, JpaSpecificationExecutor<SterilizationProtocol> {
 boolean existsByNameIgnoreCase(String name);
 boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
