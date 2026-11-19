package io.github.matrixlane.metadataregistry.web.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP（Model Context Protocol）服务端配置（add-mcp-server-support）。
 *
 * <p>加载自 {@code application.yml} 的 {@code mdr.mcp} 段：
 * <pre>
 * mdr:
 *   mcp:
 *     enabled: true            # 总开关：false 时 MCP 服务端完全不启动
 *     host: 127.0.0.1          # MCP 端口绑定地址（默认回环）
 *     port: 8082               # MCP 端口（独立于业务端口）
 *     write-tools-enabled: true # false 时只注册只读工具
 * </pre>
 */
@ConfigurationProperties(prefix = "mdr.mcp")
public class McpProperties {

    /**
     * MCP 服务端总开关。false 时不创建任何 MCP 组件（同 mdr.inference.enabled 语义）。
     */
    private boolean enabled = true;

    /**
     * MCP 端口绑定地址。默认回环——即便过滤器层另有回环校验，绑定层先行收敛暴露面。
     */
    private String host = "127.0.0.1";

    /**
     * MCP 监听端口（独立于业务端口 8081）。
     */
    private int port = 8082;

    /**
     * 受控写入工具总开关。false 时工具清单只含只读工具。
     */
    private boolean writeToolsEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isWriteToolsEnabled() {
        return writeToolsEnabled;
    }

    public void setWriteToolsEnabled(boolean writeToolsEnabled) {
        this.writeToolsEnabled = writeToolsEnabled;
    }
}
