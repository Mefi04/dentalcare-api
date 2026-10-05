package com.dentalcare.api.modules.audit.service;

import com.dentalcare.api.modules.audit.model.AuditEvent;
import com.dentalcare.api.modules.audit.model.AuditResult;
import com.dentalcare.api.modules.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.dentalcare.api.security.service.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTests {
    private final AuditEventRepository repository=mock(AuditEventRepository.class);
    private final AuditServiceImpl service=new AuditServiceImpl(repository,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test void successPersistsOnlyFixedSafeDetailAndActor() {
        UUID actor=UUID.randomUUID();
        service.success("BILLING_PAYMENT_CREATED", "BILLING", "Payment", UUID.randomUUID(), actor);
        var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(actor,captor.getValue().getActorUserId());
        assertEquals("Action completed",captor.getValue().getDetail());
        assertEquals(AuditResult.SUCCESS,captor.getValue().getResult());
    }

    @Test void failurePersistsGenericDetailAndUsesIndependentTransaction() throws Exception {
        service.failure("AUTH_LOGIN_FAILED", "AUTH", "User", null, null);
        var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals("Action failed",captor.getValue().getDetail());
        assertNull(captor.getValue().getActorUserId());
        assertEquals(Propagation.REQUIRES_NEW,AuditServiceImpl.class.getMethod("failure",String.class,String.class,String.class,UUID.class,UUID.class).getAnnotation(Transactional.class).propagation());
    }

    @Test void authenticatedPrincipalIsResolvedAndFieldsAreLimited() {
        UUID actor=UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(actor,java.util.List.of()),"n/a"));
        try {
            service.success("A".repeat(90),"B".repeat(60),"C".repeat(80),UUID.randomUUID(),null);
            var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
            verify(repository).save(captor.capture());
            assertEquals(actor,captor.getValue().getActorUserId());
            assertEquals(64,captor.getValue().getActionCode().length());
            assertEquals(40,captor.getValue().getModule().length());
            assertEquals(60,captor.getValue().getEntityType().length());
            assertEquals("Action completed",captor.getValue().getDetail());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void actionCodesAreCentralizedAndUnique() throws Exception {
        var values=java.util.Arrays.stream(AuditActions.class.getDeclaredFields())
                .filter(field->field.getType()==String.class).map(field->{
                    try { return (String)field.get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
                }).toList();
        assertTrue(values.size()>=30);
        assertEquals(values.size(),values.stream().distinct().count());
        assertTrue(values.stream().allMatch(code->code.matches("[A-Z][A-Z0-9_]+")));
    }
}
