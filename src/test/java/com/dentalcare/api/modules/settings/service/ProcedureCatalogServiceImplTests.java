package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.settings.dto.request.*;
import com.dentalcare.api.modules.settings.mapper.ProcedureCatalogItemMapper;
import com.dentalcare.api.modules.settings.model.*;
import com.dentalcare.api.modules.settings.repository.ProcedureCatalogItemRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcedureCatalogServiceImplTests {
    private ProcedureCatalogItemRepository repository; private UserRepository users; private ProcedureCatalogServiceImpl service;
    private final UUID actor=UUID.randomUUID(); private final Instant now=Instant.parse("2026-10-04T12:00:00Z");
    @BeforeEach void setUp(){repository=mock(ProcedureCatalogItemRepository.class);users=mock(UserRepository.class);
        when(users.existsById(actor)).thenReturn(true); when(repository.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        service=new ProcedureCatalogServiceImpl(repository,users,new ProcedureCatalogItemMapper(),Clock.fixed(now,ZoneOffset.UTC));}
    @Test void createNormalizesAndAudits(){
        var response=service.create(new CreateProcedureCatalogItemRequest(" proc-1 "," Evaluation "," Consultation ",30,new BigDecimal("150.00")),actor);
        assertThat(response.code()).isEqualTo("PROC-1"); assertThat(response.status()).isEqualTo(ProcedureCatalogItemStatus.ACTIVE);
        assertThat(response.createdBy()).isEqualTo(actor); assertThat(response.updatedBy()).isEqualTo(actor);
    }
    @Test void duplicateCodeIsCaseInsensitive(){when(repository.existsByCodeIgnoreCase("PROC-1")).thenReturn(true);
        assertThatThrownBy(()->service.create(request("proc-1","One"),actor)).isInstanceOf(ConflictException.class).hasMessageContaining("code");}
    @Test void duplicateNameIsCaseInsensitive(){when(repository.existsByNameIgnoreCase("Cleaning")).thenReturn(true);
        assertThatThrownBy(()->service.create(request("PROC-1","Cleaning"),actor)).isInstanceOf(ConflictException.class).hasMessageContaining("name");}
    @Test void updatePreservesStatusAndChangesActor(){var item=item(ProcedureCatalogItemStatus.ACTIVE);when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        var result=service.update(item.getId(),new UpdateProcedureCatalogItemRequest("PROC-2","Updated","Surgery",45,new BigDecimal("250.00")),actor);
        assertThat(result.name()).isEqualTo("Updated");assertThat(result.updatedBy()).isEqualTo(actor);assertThat(result.status()).isEqualTo(ProcedureCatalogItemStatus.ACTIVE);}
    @Test void statusCanMoveBothDirections(){var item=item(ProcedureCatalogItemStatus.ACTIVE);when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        assertThat(service.updateStatus(item.getId(),ProcedureCatalogItemStatus.INACTIVE,actor).status()).isEqualTo(ProcedureCatalogItemStatus.INACTIVE);
        assertThat(service.updateStatus(item.getId(),ProcedureCatalogItemStatus.ACTIVE,actor).status()).isEqualTo(ProcedureCatalogItemStatus.ACTIVE);}
    @Test void detailReturnsPersistedItem(){var item=item(ProcedureCatalogItemStatus.ACTIVE);when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        assertThat(service.findById(item.getId()).id()).isEqualTo(item.getId());}
    @Test void listAppliesFiltersAndCapsPageSize(){when(repository.findAll(any(Specification.class),any(Pageable.class))).thenReturn(Page.empty());
        service.findAll("proc","Consultation",ProcedureCatalogItemStatus.ACTIVE,2,500);
        ArgumentCaptor<Pageable> captor=ArgumentCaptor.forClass(Pageable.class);verify(repository).findAll(any(Specification.class),captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);assertThat(captor.getValue().getPageSize()).isEqualTo(100);}
    @Test void invalidPaginationIsRejected(){assertThatThrownBy(()->service.findAll(null,null,null,-1,20)).isInstanceOf(BadRequestException.class);}
    private CreateProcedureCatalogItemRequest request(String code,String name){return new CreateProcedureCatalogItemRequest(code,name,"Category",30,new BigDecimal("100.00"));}
    private ProcedureCatalogItem item(ProcedureCatalogItemStatus status){return new ProcedureCatalogItem(UUID.randomUUID(),"PROC-1","One","Category",30,
            new BigDecimal("100.00"),status,actor,actor,now,now);}
}
