package lk.ridelink.ride.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id so one passenger action can be followed across
 * all four services and the RabbitMQ messages between them.
 *
 * <p>The id is taken from the {@code X-Correlation-Id} header when the caller supplies
 * one and generated otherwise. It is placed in the SLF4J {@link MDC} (so the log
 * pattern prints it), echoed back on the response, and - in services that make
 * outgoing calls - forwarded downstream and onto event envelopes.</p>
 *
 * <p>Runs at highest precedence so the id exists before anything else can log.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused, so the id must not leak into the next request.
            MDC.remove(MDC_KEY);
        }
    }

    /** Current request's correlation id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
