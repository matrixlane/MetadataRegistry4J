package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore;
import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InitializationWizardInterceptorTest {

    private InitializationWizardInterceptor interceptor;
    private SystemSettingService systemSettingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        interceptor = new InitializationWizardInterceptor();
        systemSettingService = mock(SystemSettingService.class);
        // bootstrap 总开关默认关闭：退化为数据库判定，与变更前行为一致
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, new StandardEnvironment(),
                new InitializationStateStore(tempDir.resolve("initialization-state.yml")));
        ReflectionTestUtils.setField(interceptor, "statusProvider", provider);
    }

    private boolean invoke(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, new Object());
    }

    @Test
    void startupReadinessProbeStaysReachableBeforeInitialization() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(false);

        assertThat(invoke("/api/v1/startup/status")).isTrue();
    }

    @Test
    void uninitializedSystemStillBlocksBusinessEndpoints() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("499");
    }

    @Test
    void initializedSystemAllowsBusinessEndpoints() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);

        assertThat(invoke("/api/v1/items")).isTrue();
    }
}
