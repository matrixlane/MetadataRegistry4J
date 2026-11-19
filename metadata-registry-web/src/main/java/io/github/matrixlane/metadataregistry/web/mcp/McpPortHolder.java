package io.github.matrixlane.metadataregistry.web.mcp;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 端口实际绑定值持有者（add-mcp-server-support）：当
 * {@code mdr.mcp.port=0}（随机端口，集成测试场景）时，配置值为 0 无法用于
 * 过滤器端口判定；由连接器 start 事件回填实际 localPort。生产固定端口下
 * 回填值与配置一致。
 */
@Component
public class McpPortHolder {

    private final AtomicInteger actualPort = new AtomicInteger(-1);

    public void setActualPort(int port) {
        actualPort.set(port);
    }

    /** 实际端口已知（>0）时取实际值，否则回退配置值。 */
    public int resolve(int configuredPort) {
        int actual = actualPort.get();
        return actual > 0 ? actual : configuredPort;
    }
}
