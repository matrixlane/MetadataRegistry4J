package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code get-item-versions}（add-mcp-server-support）：条目版本历史。
 */
@Component
public class GetItemVersionsTool implements McpTool {

    private final VersionHistoryService versionHistoryService;

    public GetItemVersionsTool(VersionHistoryService versionHistoryService) {
        this.versionHistoryService = versionHistoryService;
    }

    @Override
    public String name() {
        return "get-item-versions";
    }

    @Override
    public String title() {
        return "版本历史";
    }

    @Override
    public String description() {
        return "按 UUID 查询注册条目的版本历史列表；无历史时返回空列表。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填）")),
                java.util.List.of("itemId"));
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        return versionHistoryService.listVersions(requiredUuid(arguments, "itemId"));
    }
}
