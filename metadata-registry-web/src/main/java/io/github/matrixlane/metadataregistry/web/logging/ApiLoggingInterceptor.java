package io.github.matrixlane.metadataregistry.web.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * HandlerInterceptor that logs API request entry and exit with timing information.
 * Logs: HTTP method, URI, query string, status code, and elapsed time.
 * For errors (status >= 400), also logs a brief error indicator.
 */
@Slf4j
@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "apiLoggingStartTime";
    private static final int MAX_BODY_LENGTH = 1000;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        String queryString = request.getQueryString();
        String uri = queryString != null
                ? request.getRequestURI() + "?" + queryString
                : request.getRequestURI();

        log.info("[API] {} {} - started", request.getMethod(), uri);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long elapsedMs = startTime != null ? System.currentTimeMillis() - startTime : -1;

        String queryString = request.getQueryString();
        String uri = queryString != null
                ? request.getRequestURI() + "?" + queryString
                : request.getRequestURI();

        int status = response.getStatus();
        String statusText = (status >= 400) ? " FAILED" : "";

        if (ex != null) {
            log.error("[API] {} {} → {} ({}ms) - exception: {}",
                    request.getMethod(), uri, status, elapsedMs, ex.getMessage());
        } else {
            log.info("[API] {} {} → {}{} ({}ms)",
                    request.getMethod(), uri, status, statusText, elapsedMs);
        }
    }
}
