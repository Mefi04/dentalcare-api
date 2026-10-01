package com.dentalcare.api.modules.sterilization.repository;
import com.dentalcare.api.modules.inventory.model.*;
import com.dentalcare.api.modules.inventory.repository.InventoryItemRepository;
import com.dentalcare.api.modules.sterilization.model.*;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
class SterilizationRepositoryIntegrationTests {
 private static final Instant NOW=Instant.parse("2026-10-01T12:00:00Z");
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
 @Autowired SterilizationProtocolRepository protocols; @Autowired SterilizationCycleRepository cycles; @Autowired InventoryItemRepository inventory; @Autowired UserRepository users; @Autowired EntityManager em;
 @Test void persistsCycleWithProtocolResponsibleAndExistingInstrument(){
  var protocol=protocols.saveAndFlush(new SterilizationProtocol(UUID.randomUUID(),"Vapor clínico",SterilizationMethod.STEAM,"Carga estándar","Seguir instrucciones",true,NOW,NOW));
  var user=users.saveAndFlush(new User(UUID.randomUUID(),"sterile.user","Responsable","sterile@dental.test","1234567890123","hash",UserStatus.ACTIVE,NOW,NOW));
  var item=inventory.saveAndFlush(new InventoryItem(UUID.randomUUID(),"INS-STER-1","Espejo",null,InventoryItemType.INSTRUMENT,"Clínico",InventoryItemStatus.ACTIVE,null,null,null,null,"Gabinete",1,1,NOW,NOW));
  UUID cycleId=UUID.randomUUID();cycles.saveAndFlush(new SterilizationCycle(cycleId,"EST-INTEGRATION-1",protocol,user,SterilizationCycleStatus.IN_PROGRESS,"Carga de prueba",NOW,null,Set.of(item)));em.clear();
  var found=cycles.findWithDetailsById(cycleId);
  assertThat(found).isPresent();assertThat(found.orElseThrow().getProtocol().getId()).isEqualTo(protocol.getId());assertThat(found.orElseThrow().getResponsible().getId()).isEqualTo(user.getId());assertThat(found.orElseThrow().getInstruments()).extracting(InventoryItem::getId).containsExactly(item.getId());
 }
}
