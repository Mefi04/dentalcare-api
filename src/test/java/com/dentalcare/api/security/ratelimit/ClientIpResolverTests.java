package com.dentalcare.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {
    @Test
    void ignoresForwardedHeaderWhenImmediatePeerIsNotTrusted() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("203.0.113.8", "198.51.100.4");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.8");
    }

    @Test
    void resolvesFirstUntrustedAddressFromTrustedProxyChain() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8", "192.168.0.0/16"));
        MockHttpServletRequest request = request("10.0.0.5", "198.51.100.9, 192.168.1.4");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void malformedOrExcessiveForwardingChainFallsBackToPeer() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("10.0.0.5", "attacker.example"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", String.join(",", java.util.Collections.nCopies(21, "10.0.0.1")))))
                .isEqualTo("10.0.0.5");
    }

    private ClientIpResolver resolver(List<String> trusted) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxies(trusted);
        ClientIpResolver resolver = new ClientIpResolver(properties);
        resolver.initialize();
        return resolver;
    }

    private MockHttpServletRequest request(String remote, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
