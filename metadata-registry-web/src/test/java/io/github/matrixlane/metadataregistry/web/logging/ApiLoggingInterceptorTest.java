package io.github.matrixlane.metadataregistry.web.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiLoggingInterceptorTest {

    private ApiLoggingInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new ApiLoggingInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        MDC.clear();
    }

    @Test
    void preHandle_shouldStoreStartTimeAndReturnTrue() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(request).setAttribute(eq("apiLoggingStartTime"), anyLong());
    }

    @Test
    void preHandle_shouldLogWithQueryString() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn("q=test");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void afterCompletion_shouldLogSuccess() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute("apiLoggingStartTime")).thenReturn(System.currentTimeMillis() - 100L);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void afterCompletion_shouldLogErrorStatus() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute("apiLoggingStartTime")).thenReturn(System.currentTimeMillis() - 50L);
        when(response.getStatus()).thenReturn(400);

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void afterCompletion_shouldLogException() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute("apiLoggingStartTime")).thenReturn(System.currentTimeMillis() - 200L);
        when(response.getStatus()).thenReturn(500);

        RuntimeException ex = new RuntimeException("Something went wrong");
        interceptor.afterCompletion(request, response, new Object(), ex);
    }

    @Test
    void afterCompletion_shouldHandleMissingStartTime() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/namespaces");
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute("apiLoggingStartTime")).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
