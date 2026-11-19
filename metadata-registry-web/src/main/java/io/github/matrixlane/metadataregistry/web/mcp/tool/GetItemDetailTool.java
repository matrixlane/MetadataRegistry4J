package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.AdministeredItemService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code get-item-detail}（add-mcp-server-support）：条目详情（受管链完整视图：
 * 标识、版本、注册状态 + 指称/定义）。不存在时透传业务面 404 语义。
 */
@Component
public class GetItemDetailTool implements McpTool {

    private final AdministeredItemService administeredItemService;

    public GetItemDetailTool(AdministeredItemService administeredItemService) {
        this.administeredItemService = administeredItemService;
    }

    @Override
    public String name() {
        return "get-item-detail";
    }

    @Override
    public String title() {
        return "条目详情";
    }

    @Override
    public String description() {
        return "按 UUID 查询注册条目详情，返回受管链完整视图（标识/版本/注册状态）与内容链字段（指称/定义）。"
                + "条目不存在时返回 [404] 错误。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填）")),
                java.util.List.of("itemId"));
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        return administeredItemService.get(requiredUuid(arguments, "itemId"));
    }
}
