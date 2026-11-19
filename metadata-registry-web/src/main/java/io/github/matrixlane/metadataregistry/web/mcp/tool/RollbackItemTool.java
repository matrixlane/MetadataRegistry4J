package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code rollback-item}（add-mcp-server-support）：回退管理项（受控写入）。
 * 将归属当前身份的条目回退到最近基线快照。
 */
@Component
public class RollbackItemTool implements McpTool {

    private final WorkspaceService workspaceService;
    private final ActiveRegistrarService activeRegistrarService;

    public RollbackItemTool(WorkspaceService workspaceService,
                            ActiveRegistrarService activeRegistrarService) {
        this.workspaceService = workspaceService;
        this.activeRegistrarService = activeRegistrarService;
    }

    @Override
    public String name() {
        return "rollback-item";
    }

    @Override
    public String title() {
        return "回退管理项";
    }

    @Override
    public String description() {
        return "将归属当前身份的管理项回退到最近基线快照。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填）")),
                java.util.List.of("itemId"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        UUID itemId = requiredUuid(arguments, "itemId");
        workspaceService.rollback(itemId, activeRegistrarService.currentAccountIdOrNull());
        return Map.of("itemId", itemId.toString(), "result", "ROLLED_BACK");
    }
}
