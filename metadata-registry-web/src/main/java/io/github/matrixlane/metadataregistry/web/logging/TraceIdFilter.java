package io.github.matrixlane.metadataregistry.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that generates or extracts a traceId for each HTTP request and stores it in SLF4J MDC.
 * The traceId is returned in the response header {@code X-Trace-Id} for client-side correlation.
 * <p>
 * This filter runs early in the chain (HIGHEST_PRECEDENCE + 100) to ensure traceId is available
 * for all subsequent logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
        String traceId = extractOrGenerateTraceId(request);
        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (headerTraceId != null && !headerTraceId.isBlank()) {
            return headerTraceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
