package io.github.matrixlane.metadataregistry.web.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * MCP 服务端条件装配（add-mcp-server-support 任务 2.1）：
 * enabled 开关、write-tools-enabled 向目录传递、MCP 组件缺失/存在。
 */
class McpServerConfigConditionsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpServerConfig.class, McpConnectorConfig.class))
            .withBean(McpToolCatalog.class, () -> mock(McpToolCatalog.class))
            .withBean(McpPortHolder.class, McpPortHolder::new)
            .withBean(McpEndpointGuardFilter.class, () -> mock(McpEndpointGuardFilter.class))
            .withBean(McpPortSecurityFilter.class, () -> mock(McpPortSecurityFilter.class));

    @Test
    void enabledByDefault_shouldCreateMcpBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HttpServletStreamableServerTransportProvider.class);
            assertThat(context).hasSingleBean(McpSyncServer.class);
            ServletRegistrationBean<?> registration = context.getBean("mcpTransportServlet", ServletRegistrationBean.class);
            assertThat(registration.getUrlMappings()).containsExactly(McpServerConfig.MCP_ENDPOINT);
        });
    }

    @Test
    void disabled_shouldNotCreateAnyMcpBean() {
        runner.withPropertyValues("mdr.mcp.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(HttpServletStreamableServerTransportProvider.class);
            assertThat(context).doesNotHaveBean(McpSyncServer.class);
            assertThat(context).doesNotHaveBean("mcpTransportServlet");
        });
    }

    @Test
    void writeToolsDisabled_shouldPassFalseToCatalog() {
        runner.withPropertyValues("mdr.mcp.write-tools-enabled=false").run(context -> {
            McpSyncServer server = context.getBean(McpSyncServer.class);
            assertThat(server).isNotNull();
            McpToolCatalog catalog = context.getBean(McpToolCatalog.class);
            verify(catalog).specifications(false);
        });
    }

    @Test
    void writeToolsEnabledByDefault_shouldPassTrueToCatalog() {
        runner.run(context -> {
            McpToolCatalog catalog = context.getBean(McpToolCatalog.class);
            verify(catalog).specifications(true);
        });
    }

    @Test
    void catalogSpecifications_shouldBeUsableForServerBuild() {
        // 目录与 SDK 集成的烟守护：真实目录 + 桩工具 → 规格可构建
        McpToolCatalog real = new McpToolCatalog(
                List.of(new McpToolCatalogTest.StubTool("search-metadata", false, java.util.Map.of())),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(io.github.matrixlane.metadataregistry.web.mcp.audit.McpToolAuditService.class));
        assertThat(real.specifications(true)).hasSize(1);
    }
}
