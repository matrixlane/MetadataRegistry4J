package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 端点前置守卫（add-mcp-server-support，评审 MAJOR-B1）：
 * 业务端口 404、非回环 403 在身份解析（security chain）之前生效——
 * 匿名请求同样得到 403/404 而非全局链的 401。
 */
class McpEndpointGuardFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private final McpProperties properties = new McpProperties();
    private final McpEndpointGuardFilter filter = new McpEndpointGuardFilter(
            providerOf(properties), providerOf(new McpPortHolder()), objectMapper);

    @Test
    void businessPortMcpPath_shouldReturn404() throws Exception {
        MockHttpServletResponse response = invoke(request(8081, "127.0.0.1", "/mcp"));
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void anonymousBusinessPortMcpPath_shouldAlsoReturn404() throws Exception {
        // 关键场景（评审 B1）：无凭证时也不能被全局安全链抢先 401
        MockHttpServletResponse response = invoke(request(8081, "127.0.0.1", "/mcp"));
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void nonLoopbackSource_shouldReturn403() throws Exception {
        MockHttpServletResponse response = invoke(request(8082, "192.168.1.10", "/mcp"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void loopbackMcpPort_shouldPassThrough() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(8082, "127.0.0.1", "/mcp"), response, chainRecording(invoked));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void nonMcpPath_shouldBeSkipped() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(8082, "127.0.0.1", "/api/v1/metadata/search"), response,
                chainRecording(invoked));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void pathVariantWithDoubleSlash_shouldStillBeHandled() throws Exception {
        MockHttpServletRequest request = request(8081, "127.0.0.1", "//mcp");
        MockHttpServletResponse response = invoke(request);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    // ---- helpers ----

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainRecording(new AtomicBoolean()));
        return response;
    }

    private static MockHttpServletRequest request(int localPort, String remoteAddr, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(uri);
        request.setServletPath(uri);
        request.setLocalPort(localPort);
        request.setRemoteAddr(remoteAddr);
        request.setContentType("application/json");
        return request;
    }

    private static jakarta.servlet.FilterChain chainRecording(AtomicBoolean invoked) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                invoked.set(true);
            }
        };
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> providerOf(T value) {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }
        };
    }
}
