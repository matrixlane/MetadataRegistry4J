package io.github.matrixlane.metadataregistry.web.mcp;

import java.util.Map;

/**
 * MCP 工具贡献 SPI（add-mcp-server-support）。每个工具 bean 描述一个
 * {@code mcp__mdr__<name>} 工具的契约：输入 JSON Schema、语义描述与处理器。
 *
 * <p>写工具 MUST 覆写 {@link #write()} 返回 {@code true}——受控写入工具受
 * {@code mdr.mcp.write-tools-enabled} 注册开关与 REGISTRAR 角色门禁双重约束。
 */
public interface McpTool {

    /** 工具名（kebab-case，客户端可见全名为 mcp__mdr__&lt;name&gt;）。 */
    String name();

    /** 显示名（中文）。 */
    String title();

    /** 语义描述：说明能力、边界与关键约束，供 Agent 决策。 */
    String description();

    /** 输入参数 JSON Schema（type=object）。 */
    Map<String, Object> inputSchema();

    /** 是否为受控写入工具。默认 false（只读）。 */
    default boolean write() {
        return false;
    }

    /**
     * 执行工具。允许抛出 {@link io.github.matrixlane.metadataregistry.web.common.BusinessException}
     * （经统一封装为 {@code [code] message} 错误结果）；其余异常由目录层兜底为通用错误。
     */
    Object handle(Map<String, Object> arguments);
}
