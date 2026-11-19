package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 端口安全过滤器（add-mcp-server-support，设计稿 D6）。
 *
 * <p>身份语义复用既有全局过滤器：个人版隐式 SUPER_ADMIN 与团队版 Bearer JWT 分别由
 * {@code PersonalEditionAuthenticationFilter} / {@code JwtAuthenticationFilter} 装配
 * （本过滤器在其后执行，只读 SecurityContext，不改变认证语义）。
 * 依赖经 {@link ObjectProvider} 注入：@WebMvcTest 等切片缺失 MCP 组件时降级为
 * no-op（同既有 personal 过滤器的切片约定）。
 *
 * <p>规则（按序；404 隐藏与非回环 403 由更早的 {@link McpEndpointGuardFilter} 先行处理）：
 * <ol>
 *   <li>MCP 端口未认证 → 401（团队版无/无效 Bearer 的天然落点）；</li>
 *   <li>每身份令牌桶 30 req/s → 429；</li>
 *   <li>JSON-RPC {@code tools/call} 调写入工具而无 REGISTRAR 角色 → 403
 *       （body 以可重复读包装传递；解析失败按潜在写工具收紧）；非 JSON/非 POST 放行。</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mdr.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpPortSecurityFilter extends OncePerRequestFilter {

    /** 每身份速率上限（req/s）。 */
    static final int RATE_LIMIT_PER_SECOND = 30;

    private final ObjectProvider<McpProperties> propertiesProvider;
    private final ObjectProvider<McpToolCatalog> toolCatalogProvider;
    private final ObjectProvider<McpPortHolder> portHolderProvider;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public McpPortSecurityFilter(ObjectProvider<McpProperties> propertiesProvider,
                                 ObjectProvider<McpToolCatalog> toolCatalogProvider,
                                 ObjectProvider<McpPortHolder> portHolderProvider,
                                 ObjectMapper objectMapper) {
        this.propertiesProvider = propertiesProvider;
        this.toolCatalogProvider = toolCatalogProvider;
        this.portHolderProvider = portHolderProvider;
        this.objectMapper = objectMapper;
    }

    /** 切片（@WebMvcTest）等无 MCP 组件的场景下降级为 no-op（同既有 personal 过滤器约定）。 */
    private McpProperties propertiesOrNull() {
        return propertiesProvider.getIfAvailable();
    }

    private int mcpPort() {
        McpProperties properties = propertiesOrNull();
        if (properties == null) {
            return -1;
        }
        McpPortHolder holder = portHolderProvider.getIfAvailable();
        return holder == null ? properties.getPort() : holder.resolve(properties.getPort());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        McpProperties properties = propertiesOrNull();
        if (properties == null) {
            return true;
        }
        return request.getLocalPort() != mcpPort()
                && !McpServerConfig.MCP_ENDPOINT.equals(McpEndpointGuardFilter.normalizePath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!McpServerConfig.MCP_ENDPOINT.equals(McpEndpointGuardFilter.normalizePath(request))) {
            // MCP 端口上的非 MCP 路径（actuator 等）：交回业务规则，本过滤器不插手
            filterChain.doFilter(request, response);
            return;
        }
        // 404（业务端口）与非回环 403 已由前置守卫 McpEndpointGuardFilter 处理
        if (!McpIdentitySupport.isAuthenticated()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ApiCodes.UNAUTHORIZED, "未认证");
            return;
        }
        if (!allow(identityKey(request))) {
            writeJson(response, 429, 429, "调用频率超限");
            return;
        }
        BodyCachingRequest wrapped = new BodyCachingRequest(request);
        if (isWriteToolCall(wrapped) && !McpIdentitySupport.hasAnyRole("REGISTRAR")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, ApiCodes.FORBIDDEN,
                    "写入工具需要 REGISTRAR 角色");
            return;
        }
        filterChain.doFilter(wrapped, response);
    }

    private String identityKey(HttpServletRequest request) {
        // 审计标签对未认证返回 anonymous@<addr>，直接采用避免重复前缀（评审 MINOR-A9）
        return McpIdentitySupport.identityLabel(request);
    }

    /** 解析 JSON-RPC body 判定是否为受控写入工具的 tools/call。
     *  解析失败按潜在写工具收紧：弱角色收到 403 而非静默放行（评审 MAJOR-A5）。 */
    private boolean isWriteToolCall(BodyCachingRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/json")) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(request.getCachedBody());
            if (!"tools/call".equals(root.path("method").asText())) {
                return false;
            }
            String toolName = root.path("params").path("name").asText("");
            McpToolCatalog catalog = toolCatalogProvider.getIfAvailable();
            return catalog != null && catalog.isWriteTool(toolName);
        } catch (IOException e) {
            log.debug("MCP 请求体非合法 JSON，按潜在写工具收紧: {}", e.getMessage());
            return true;
        }
    }

    /** 令牌桶：容量与补充速率均为 RATE_LIMIT_PER_SECOND；refill 经饱和计算防 long 溢出（评审 MAJOR-A4）。 */
    private boolean allow(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        long now = System.nanoTime();
        synchronized (bucket) {
            long elapsedNanos = Math.max(0, now - bucket.lastRefillNanos);
            long refill = elapsedNanos / (1_000_000_000L / RATE_LIMIT_PER_SECOND);
            if (refill > 0) {
                bucket.tokens = Math.min(RATE_LIMIT_PER_SECOND,
                        Math.addExact(bucket.tokens, Math.min(refill, RATE_LIMIT_PER_SECOND)));
                bucket.lastRefillNanos = now;
            }
            if (bucket.tokens > 0) {
                bucket.tokens--;
                return true;
            }
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }

    private static final class Bucket {
        long tokens = RATE_LIMIT_PER_SECOND;
        long lastRefillNanos = System.nanoTime();
    }

    /** 可重复读 body 的请求包装：过滤器解析 JSON-RPC 后 SDK servlet 仍需读取。 */
    static final class BodyCachingRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BodyCachingRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    // 同步读取，无需监听
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
