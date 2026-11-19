package io.github.matrixlane.metadataregistry.web.mcp;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 双连接器（add-mcp-server-support 任务 2.x）：附加 Connector 绑定
 * 配置地址/端口。
 */
class McpConnectorConfigTest {

    @Test
    void customizer_shouldAddConnectorWithConfiguredHostAndPort() {
        McpProperties properties = new McpProperties();
        properties.setPort(9082);
        properties.setHost("127.0.0.1");

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new McpConnectorConfig().mcpConnectorCustomizer(properties, new McpPortHolder()).customize(factory);

        List<Connector> connectors = factory.getAdditionalTomcatConnectors();
        assertThat(connectors).hasSize(1);
        Connector connector = connectors.get(0);
        assertThat(connector.getPort()).isEqualTo(9082);
        // Tomcat 将 address 属性转为 InetAddress，回读形态为 "/127.0.0.1"
        assertThat(String.valueOf(connector.getProperty("address"))).endsWith("127.0.0.1");
    }
}
