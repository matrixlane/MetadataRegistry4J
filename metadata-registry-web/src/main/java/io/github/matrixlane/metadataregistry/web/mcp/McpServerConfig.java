package io.github.matrixlane.metadataregistry.web.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 服务端装配（add-mcp-server-support，设计稿 D1/D2）。
 *
 * <p>legacy（2025-11-25）协议代际 + Streamable HTTP（SDK 2.2.0
 * {@link HttpServletStreamableServerTransportProvider}，Servlet 传输注册于
 * {@link #MCP_ENDPOINT}）。MCP servlet 对整个上下文可见（业务端口与 MCP 端口），
 * 暴露面由 {@code McpPortSecurityFilter} 按 localPort 收敛——业务端口访问
 * MCP 路径返回 404。
 *
 * <p>{@code mdr.mcp.enabled=false} 时本配置不装配任何 MCP 组件（无监听）。
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "mdr.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfig {

    /** MCP Streamable HTTP 端点路径（Kimi Code 等客户端 url 指向 http://host:port/mcp）。 */
    public static final String MCP_ENDPOINT = "/mcp";

    /** 服务端标识：name 与客户端 mcp.json 的 server key 对齐（工具全名 mcp__mdr__<tool>）。 */
    public static final String SERVER_NAME = "mdr";
    public static final String SERVER_VERSION = "1.0.0";

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(MCP_ENDPOINT)
                .build();
    }

    /**
     * 前置守卫（404/回环 403）必须早于 springSecurityFilterChain（order=-100）执行，
     * 否则匿名请求先被全局链 401，spec 的 403/404 场景失真（评审 MAJOR-B1）。
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<McpEndpointGuardFilter>
            mcpGuardFilterRegistration(McpEndpointGuardFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** 身份/限流/写门禁保留在 security chain 之后（默认最低优先级）。 */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<McpPortSecurityFilter>
            mcpPortSecurityFilterRegistration(McpPortSecurityFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpTransportServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
        registration.setName("mcpTransportServlet");
        return registration;
    }

    /** McpSyncServer 必须持有为 bean：server 生命周期随上下文，destroy 时 close。 */
    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider,
                                       McpToolCatalog toolCatalog,
                                       McpProperties properties) {
        // immediateExecution(true)：Servlet 传输是阻塞模型，工具调用必须在请求线程执行——
        // 否则 SDK 默认把调用调度到 Reactor boundedElastic 线程，SecurityContextHolder
        // 的 ThreadLocal 身份丢失（审计恒 anonymous、写工具归属推导失败）。
        // 协议版本：SDK 2.2.0 legacy 传输固定支持 2025-03-26/06-18/11-25，
        // 协商取客户端请求版本（e2e 实证 2025-11-25）；锁版本防漂移。
        return McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .tools(toolCatalog.specifications(properties.isWriteToolsEnabled()))
                .immediateExecution(true)
                .build();
    }
}
