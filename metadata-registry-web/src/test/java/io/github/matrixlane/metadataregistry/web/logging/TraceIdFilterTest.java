package io.github.matrixlane.metadataregistry.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private TraceIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateTraceId_whenHeaderNotPresent() throws ServletException, IOException {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), argThat(traceId ->
                traceId != null && traceId.length() == 32
        ));
        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull(); // cleared in finally
    }

    @Test
    void shouldExtractTraceId_fromHeader() throws ServletException, IOException {
        String existingTraceId = "abc123def456";
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(existingTraceId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(TraceIdFilter.TRACE_ID_HEADER, existingTraceId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldTrimTraceId_fromHeader() throws ServletException, IOException {
        String existingTraceId = "  abc123def456  ";
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(existingTraceId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(TraceIdFilter.TRACE_ID_HEADER, "abc123def456");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldGenerateTraceId_whenHeaderIsBlank() throws ServletException, IOException {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), argThat(traceId ->
                traceId != null && traceId.length() == 32
        ));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearMdcInFinallyBlock_evenOnException() throws ServletException, IOException {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(null);
        doThrow(new IOException("test")).when(filterChain).doFilter(request, response);

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
                filter.doFilterInternal(request, response, filterChain)
        );

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
