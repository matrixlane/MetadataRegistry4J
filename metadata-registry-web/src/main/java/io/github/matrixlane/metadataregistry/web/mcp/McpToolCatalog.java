package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.mcp.audit.McpToolAuditService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具目录（add-mcp-server-support）：把全部 {@link McpTool} bean 装配为
 * SDK 的 {@link McpServerFeatures.SyncToolSpecification}，并统一封装：
 *
 * <ul>
 *   <li>结果序列化：String 原样返回，其余经 Jackson 转 JSON 文本；</li>
 *   <li>错误语义：{@link BusinessException} → {@code [code] message}（isError=true）；
 *       {@link io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException}
 *       → {@code [404] message}（isError=true，未找到是业务语义而非内部错误）；
 *       其余异常 → 通用 {@code [500] MCP 工具执行失败（内部错误）}（不泄露堆栈/连接串，
 *       细节仅落服务端日志）；</li>
 *   <li>审计：每次调用记录参数 SHA-256 摘要与结果状态。</li>
 * </ul>
 *
 * <p>写入工具受 {@code writeToolsEnabled} 过滤——{@code false} 时不出现在工具清单。
 */
@Slf4j
@Component
public class McpToolCatalog {

    private final List<McpTool> tools;
    private final ObjectMapper objectMapper;
    private final McpToolAuditService auditService;

    public McpToolCatalog(List<McpTool> tools,
                          ObjectMapper objectMapper,
                          McpToolAuditService auditService) {
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    /** 装配工具规格；{@code includeWrite=false} 时仅只读工具。 */
    public List<McpServerFeatures.SyncToolSpecification> specifications(boolean includeWrite) {
        return tools.stream()
                .filter(tool -> includeWrite || !tool.write())
                .map(this::toSpecification)
                .toList();
    }

    /**
     * 工具名是否属受控写入工具（安全过滤器按名做 REGISTRAR 门禁）。
     * 与 {@code write-tools-enabled} 注册开关无关——未注册的写工具名同样拒绝弱角色调用。
     */
    public boolean isWriteTool(String name) {
        return tools.stream().anyMatch(tool -> tool.write() && tool.name().equals(name));
    }

    private McpServerFeatures.SyncToolSpecification toSpecification(McpTool tool) {
        McpSchema.Tool definition = McpSchema.Tool.builder(tool.name(), tool.inputSchema())
                .title(tool.title())
                .description(tool.description())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(definition)
                .callHandler((exchange, request) -> invoke(tool, request.arguments()))
                .build();
    }

    /** 统一调用封装：结果序列化 + 错误语义 + 审计。 */
    McpSchema.CallToolResult invoke(McpTool tool, Map<String, Object> arguments) {
        long start = System.nanoTime();
        String digest = digest(arguments);
        try {
            Object result = tool.handle(arguments == null ? Map.of() : arguments);
            auditService.record(tool.name(), McpIdentitySupport.identityLabel(null), digest,
                    "OK", elapsedMs(start));
            return textResult(serialized(result), false);
        } catch (BusinessException e) {
            auditService.record(tool.name(), McpIdentitySupport.identityLabel(null), digest,
                    "BUSINESS_ERROR", elapsedMs(start));
            return textResult("[" + e.getCode() + "] " + e.getMessage(), true);
        } catch (io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException e) {
            // 未找到是业务语义（如 get-item-detail 条目不存在），须透传 [404] 而不是
            // 归类为内部错误——否则 MCP 客户端看到误导性的 [500]（2026-11-20 事故）
            auditService.record(tool.name(), McpIdentitySupport.identityLabel(null), digest,
                    "NOT_FOUND", elapsedMs(start));
            return textResult("[" + ApiCodes.NOT_FOUND + "] " + e.getMessage(), true);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            // 归属/角色门禁（ActiveRegistrarService 等）抛 AccessDeniedException：
            // 归类为权限错误而非内部错误，保持 [code] message 语义（评审 MAJOR-2）
            auditService.record(tool.name(), McpIdentitySupport.identityLabel(null), digest,
                    "FORBIDDEN", elapsedMs(start));
            return textResult("[" + ApiCodes.FORBIDDEN + "] " + e.getMessage(), true);
        } catch (Exception e) {
            log.error("MCP 工具 {} 执行异常（参数摘要 {}）", tool.name(), digest, e);
            auditService.record(tool.name(), McpIdentitySupport.identityLabel(null), digest,
                    "INTERNAL_ERROR", elapsedMs(start));
            return textResult("[" + ApiCodes.INTERNAL_ERROR + "] MCP 工具执行失败（内部错误）", true);
        }
    }

    private McpSchema.CallToolResult textResult(String text, boolean error) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(McpSchema.TextContent.builder(text).build()))
                .isError(error)
                .build();
    }

    private String serialized(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("MCP 工具结果序列化失败，降级为 toString", e);
            return String.valueOf(result);
        }
    }

    private static String digest(Map<String, Object> arguments) {
        // 经 ObjectMapper 序列化后取摘要：Map.toString() 的 key 顺序依赖实现，重放同一
        // 逻辑参数可能产生不同摘要（评审 MINOR-6）
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new ObjectMapper().writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            return "sha256-unavailable";
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
