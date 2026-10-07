package com.dentalcare.api.security.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private static final int MAX_FORWARDED_HOPS = 20;
    private final RateLimitProperties properties;
    private List<IpAddressMatcher> trustedProxies = List.of();

    public ClientIpResolver(RateLimitProperties properties) { this.properties = properties; }

    @PostConstruct
    void initialize() {
        trustedProxies = properties.getTrustedProxies().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalizeLiteral(request.getRemoteAddr());
        if (remote == null) return "unknown";
        if (!isTrusted(remote)) return remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String[] raw = forwarded.split(",");
        if (raw.length > MAX_FORWARDED_HOPS) return remote;
        List<String> chain = new ArrayList<>(raw.length);
        for (String token : raw) {
            String address = normalizeLiteral(token.trim());
            if (address == null) return remote;
            chain.add(address);
        }
        for (int index = chain.size() - 1; index >= 0; index--) {
            if (!isTrusted(chain.get(index))) return chain.get(index);
        }
        return chain.isEmpty() ? remote : chain.getFirst();
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String normalizeLiteral(String value) {
        if (value == null || value.isBlank() || !isIpLiteral(value)) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isIpLiteral(String value) {
        if (value.indexOf(':') >= 0) return value.matches("[0-9A-Fa-f:.%]+");
        return value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    }
}
