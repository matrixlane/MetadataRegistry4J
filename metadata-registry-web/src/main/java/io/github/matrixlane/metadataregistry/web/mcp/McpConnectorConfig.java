package io.github.matrixlane.metadataregistry.web.mcp;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 双连接器（add-mcp-server-support，设计稿 D2/D4）：在业务 Tomcat 上追加
 * 绑定 {@code mdr.mcp.host:mdr.mcp.port} 的独立 Connector，与业务端口隔离。
 *
 * <p>端口被占用等绑定失败由 Tomcat 启动期快速失败（可读 BindException 日志），
 * 不静默降级。绑定层回环先行收敛暴露面；过滤器层另有非回环 403 双保险。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "mdr.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpConnectorConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mcpConnectorCustomizer(
            McpProperties properties, McpPortHolder portHolder) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(properties.getPort());
            connector.setProperty("address", properties.getHost());
            // 随机端口（port=0）场景：绑定完成后回填实际 localPort，供安全过滤器判定。
            // 监听 start 与 after_start 双事件并守卫 lp>0：NIO 绑定时序下 localPort
            // 仅在协议处理器启动完成后可用。成功绑定日志也移至 after_start，
            // 避免"打印 listening 但绑定失败/端口为 0"的误导（评审 MINOR-A8/B6）。
            connector.addLifecycleListener(event -> {
                if ("start".equals(event.getType()) || "after_start".equals(event.getType())) {
                    int localPort = connector.getLocalPort();
                    if (localPort > 0) {
                        portHolder.setActualPort(localPort);
                    }
                }
                if ("after_start".equals(event.getType())) {
                    log.info("MCP server listening on {}:{}{}（legacy / Streamable HTTP）",
                            connector.getProperty("address"), connector.getLocalPort(),
                            McpServerConfig.MCP_ENDPOINT);
                }
            });
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
