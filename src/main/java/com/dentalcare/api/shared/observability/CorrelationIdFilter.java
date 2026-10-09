package com.dentalcare.api.shared.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Never trusts or echoes a caller-supplied identifier. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String ID_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class);
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException { doFilterInternal(request, response, chain); }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String previous = MDC.get("request_id");
        String id = (String) request.getAttribute(ID_ATTRIBUTE);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(ID_ATTRIBUTE, id);
        }
        response.setHeader("X-Request-ID", id);
        MDC.put("request_id", id);
        long started = System.nanoTime();
        boolean escaped = false;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException | Error error) { escaped = true; throw error; }
        finally {
            try {
                var event = LOGGER.atInfo().addKeyValue("event", "http_request")
                    .addKeyValue("duration_ms", (System.nanoTime() - started) / 1_000_000L);
                // The container may decide the status only in a later ERROR dispatch.
                // Omit an undetermined status rather than publishing a false 200 or guessing 500.
                if (!escaped || response.getStatus() >= 400) event.addKeyValue("status", response.getStatus());
                event.log("HTTP dispatch completed");
            } finally {
                if (previous == null) MDC.remove("request_id"); else MDC.put("request_id", previous);
            }
        }
    }
}
