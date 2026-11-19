package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.mcp.audit.McpToolAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MCP 端口安全过滤器（add-mcp-server-support 任务 3.x）：
 * 404 隐藏、403 回环、401 未认证、429 限流、写工具 REGISTRAR 门禁、
 * body 可重复读。
 */
class McpPortSecurityFilterTest {

    private static final int MCP_PORT = 8082;
    private static final String TOOLS_CALL_DRAFT = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"draft-item","arguments":{}}}""";

    private final McpProperties properties = new McpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private final McpToolCatalog catalog = new McpToolCatalog(
            List.of(new McpToolCatalogTest.StubTool("search-metadata", false, Map.of()),
                    new McpToolCatalogTest.StubTool("draft-item", true, Map.of())),
            objectMapper, mock(McpToolAuditService.class));
    private final McpPortSecurityFilter filter =
            new McpPortSecurityFilter(providerOf(properties), providerOf(catalog),
                    providerOf(new McpPortHolder()), objectMapper);

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

    @BeforeEach
    void setUp() {
        properties.setPort(MCP_PORT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writeToolCallWithoutRegistrarRole_shouldReturn403() throws Exception {
        authenticate("VIEWER");
        MockHttpServletResponse response = invoke(toolCallRequest(MCP_PORT, "127.0.0.1"), new AtomicBoolean());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void writeToolCallWithRegistrarRole_shouldPassThrough() throws Exception {
        authenticate("REGISTRAR");
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(toolCallRequest(MCP_PORT, "127.0.0.1"), invoked);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void readToolCallWithPlainAuthenticatedRole_shouldPassThrough() throws Exception {
        authenticate("VIEWER");
        MockHttpServletRequest request = toolCallRequest(MCP_PORT, "127.0.0.1");
        replaceToolName(request, "search-metadata");
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request, invoked);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void nonMcpPathOnMcpPort_shouldPassThroughUnblocked() throws Exception {
        authenticate("VIEWER");
        MockHttpServletRequest request = baseRequest(MCP_PORT, "127.0.0.1");
        request.setServletPath("/actuator/health");
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request, invoked);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void businessPortNonMcpPath_shouldBeSkipped() throws Exception {
        MockHttpServletRequest request = baseRequest(8081, "127.0.0.1");
        request.setServletPath("/api/v1/metadata/search");
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request, invoked);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
    }

    @Test
    void beyondRateLimit_shouldReturn429() throws Exception {
        authenticate("REGISTRAR");
        // JIT 预热：用一次性过滤器跑 100 次，避免测量段因编译/分配毛刺越过回补线
        McpPortSecurityFilter warmup = new McpPortSecurityFilter(providerOf(properties),
                providerOf(catalog), providerOf(new McpPortHolder()), objectMapper);
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setServletPath(McpServerConfig.MCP_ENDPOINT);
            request.setLocalPort(MCP_PORT);
            request.setRemoteAddr("127.0.0.1");
            warmup.doFilter(request, new MockHttpServletResponse(), chainRecording(new AtomicBoolean()));
        }
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < McpPortSecurityFilter.RATE_LIMIT_PER_SECOND + 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setServletPath(McpServerConfig.MCP_ENDPOINT);
            request.setLocalPort(MCP_PORT);
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = invoke(request, new AtomicBoolean());
            if (response.getStatus() == 200) {
                accepted++;
            } else if (response.getStatus() == 429) {
                rejected++;
            }
        }
        assertThat(accepted).isEqualTo(McpPortSecurityFilter.RATE_LIMIT_PER_SECOND);
        assertThat(rejected).isEqualTo(10);
    }

    @Test
    void wrappedBody_shouldBeReadableDownstream() throws Exception {
        authenticate("REGISTRAR");
        MockHttpServletRequest request = toolCallRequest(MCP_PORT, "127.0.0.1");
        String original = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
        AtomicReference<String> downstreamBody = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            downstreamBody.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamBody.get()).isEqualTo(original);
    }

    // ---- helpers ----

    private MockHttpServletResponse invoke(MockHttpServletRequest request, AtomicBoolean invoked)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainRecording(invoked));
        return response;
    }

    private static FilterChain chainRecording(AtomicBoolean invoked) {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                invoked.set(true);
            }
        };
    }

    private static MockHttpServletRequest baseRequest(int localPort, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath(McpServerConfig.MCP_ENDPOINT);
        request.setLocalPort(localPort);
        request.setRemoteAddr(remoteAddr);
        request.setContentType("application/json");
        request.setContent(TOOLS_CALL_DRAFT.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletRequest toolCallRequest(int localPort, String remoteAddr) {
        return baseRequest(localPort, remoteAddr);
    }

    private static void replaceToolName(MockHttpServletRequest request, String toolName) {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"%s","arguments":{}}}"""
                .formatted(toolName);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate(String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), "agent", null);
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
