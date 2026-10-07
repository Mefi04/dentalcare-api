package com.dentalcare.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class RateLimitServiceTests {
    @Test
    void blockedIdentityReturns429SemanticsAndAuditsWithoutIdentity() {
        Fixture fixture = fixture();
        when(fixture.repository.consume(any(), anyInt(), any())).thenReturn(RateLimitDecision.blocked(42));

        assertThatThrownBy(() -> fixture.service.checkIdentity(RateLimitPolicy.LOGIN, "1234567890123"))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("retryAfterSeconds").isEqualTo(42L);
        verify(fixture.audit).failure(AuditActions.SECURITY_RATE_LIMIT_BLOCKED, "SECURITY", "LOGIN", null, null);
    }

    @Test
    void identitiesUseIndependentOpaqueBuckets() {
        Fixture fixture = fixture();
        when(fixture.repository.consume(any(), anyInt(), any())).thenReturn(RateLimitDecision.permit());
        fixture.service.checkIdentity(RateLimitPolicy.LOGIN, "1234567890123");
        fixture.service.checkIdentity(RateLimitPolicy.LOGIN, "9999999999999");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(fixture.repository, org.mockito.Mockito.times(2)).consume(keys.capture(), eq(10), any());
        Set<String> values = new HashSet<>(keys.getAllValues());
        org.assertj.core.api.Assertions.assertThat(values).hasSize(2).allMatch(key ->
                key.startsWith("LOGIN:identity:") && !key.contains("1234567890123") && !key.contains("9999999999999"));
    }

    @Test
    void disabledLimiterDoesNotTouchSharedStore() {
        Fixture fixture = fixture();
        fixture.properties.setEnabled(false);
        fixture.service.checkIdentity(RateLimitPolicy.LOGIN, "1234567890123");
        fixture.service.checkIp(RateLimitPolicy.LOGIN, mock(HttpServletRequest.class));
        verify(fixture.repository, never()).consume(any(), anyInt(), any());
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        DatabaseRateLimitRepository repository = mock(DatabaseRateLimitRepository.class);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        RateLimitProperties properties = new RateLimitProperties();
        AuditService audit = mock(AuditService.class);
        ObjectProvider<AuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(audit);
        return new Fixture(repository, properties, audit,
                new RateLimitService(repository, resolver, properties, provider));
    }

    private record Fixture(DatabaseRateLimitRepository repository, RateLimitProperties properties,
                           AuditService audit, RateLimitService service) { }
}
