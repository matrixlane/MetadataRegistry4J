package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.service.FactoryResetService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * MCP 端点前置守卫（add-mcp-server-support，评审 MAJOR-B1）：经
 * {@code FilterRegistrationBean} 注册为早于 springSecurityFilterChain
 * （order=-100）的过滤器，使"隐藏端点/回环边界"规则对**匿名请求同样生效**——
 * 若由全局安全链先行，匿名非回环请求会先被 401 而非 403、匿名业务端口访问
 * MCP 路径会先被 401 而非 404（spec 场景失真）。
 *
 * <p>仅处理 MCP 路径，规则（先于身份解析）：
 * <ol>
 *   <li>业务端口访问 MCP 路径 → 404；</li>
 *   <li>非回环来源（纯字面量判定，复用
 *       {@link FactoryResetService#isLoopbackAddressLiteral}）→ 403。</li>
 * </ol>
 * 其余放行；身份（401）、限流（429）与写工具角色门禁仍由 security chain 之后的
 * {@link McpPortSecurityFilter} 处理。
 *
 * <p>依赖经 {@link ObjectProvider} 注入：@WebMvcTest 等切片缺失 MCP 组件时
 * 降级为 no-op（同既有 personal 过滤器的切片约定）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mdr.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpEndpointGuardFilter extends OncePerRequestFilter {

    private final ObjectProvider<McpProperties> propertiesProvider;
    private final ObjectProvider<McpPortHolder> portHolderProvider;
    private final ObjectMapper objectMapper;

    public McpEndpointGuardFilter(ObjectProvider<McpProperties> propertiesProvider,
                                  ObjectProvider<McpPortHolder> portHolderProvider,
                                  ObjectMapper objectMapper) {
        this.propertiesProvider = propertiesProvider;
        this.portHolderProvider = portHolderProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return propertiesProvider.getIfAvailable() == null
                || !McpServerConfig.MCP_ENDPOINT.equals(normalizePath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        McpProperties properties = propertiesProvider.getIfAvailable();
        McpPortHolder holder = portHolderProvider.getIfAvailable();
        int mcpPort = holder == null ? properties.getPort() : holder.resolve(properties.getPort());
        if (request.getLocalPort() != mcpPort) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiCodes.NOT_FOUND, "Not Found");
            return;
        }
        if (!FactoryResetService.isLoopbackAddressLiteral(request.getRemoteAddr())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, ApiCodes.FORBIDDEN,
                    "MCP 端口仅允许回环访问");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 严格路径判定：URI 去 contextPath 后折叠重复斜杠，防 /mcp/../mcp、//mcp 等变体绕过（评审 MINOR-A7）。
     *  Mock 请求未设 requestURI 时回退 servletPath。 */
    static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            uri = request.getServletPath();
        }
        if (uri == null) {
            uri = "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        while (uri.contains("//")) {
            uri = uri.replace("//", "/");
        }
        return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
