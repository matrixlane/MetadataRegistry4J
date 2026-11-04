package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.ApplicationRestartExecutor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatasourceRestartExitFilterTest {

    private final ApplicationRestartExecutor exitExecutor = mock(ApplicationRestartExecutor.class);
    private final DatasourceRestartExitFilter filter = new DatasourceRestartExitFilter(provider());

    @Test
    void schedulesExitAfterAcceptedApplyResponse() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/datasource/config/apply");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(202);

        filter.doFilter(request, response, chain);

        verify(exitExecutor).scheduleExit();
    }

    @Test
    void schedulesExitAfterAcceptedConfirmCurrentResponse() throws Exception {
        // 事故回归（2026-10-27）：向导「保持当前数据源并继续」经 confirm-current
        // 复用同一重启事务，旧过滤器只认 apply 路径，后端不退出导致事务永远 PENDING。
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/datasource/config/confirm-current");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(202);

        filter.doFilter(request, response, chain);

        verify(exitExecutor).scheduleExit();
    }

    @Test
    void doesNotScheduleExitWhenApplyWasRejected() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/datasource/config/apply");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(409);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(exitExecutor);
    }

    @Test
    void doesNotScheduleExitForOtherEndpoints() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/datasource/config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(exitExecutor);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ApplicationRestartExecutor> provider() {
        ObjectProvider<ApplicationRestartExecutor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(exitExecutor);
        return provider;
    }
}
