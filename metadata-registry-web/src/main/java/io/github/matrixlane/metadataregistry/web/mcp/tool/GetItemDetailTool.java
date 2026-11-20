package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code get-item-detail}（add-mcp-server-support）：条目详情（受管链完整视图：
 * 标识、版本、注册状态 + 指称）。不存在时透传业务面 404 语义。
 *
 * <p>走 MetadataSearchService 标量 SQL 路径——实体路径在条目关联填满后会触发
 * SQLite 64 表 join 上限（KNOWN_FAILURES），返回 500。</p>
 */
@Component
public class GetItemDetailTool implements McpTool {

    private final MetadataSearchService searchService;

    public GetItemDetailTool(MetadataSearchService searchService) {
        this.searchService = searchService;
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
        return "按 UUID 查询注册条目详情，返回受管链完整视图（标识/版本/注册状态）与内容链字段（指称）。"
                + "接受受管项 UUID 或内容链 itemId UUID（browse/search 结果中的 id / itemId 字段均可）。"
                + "条目不存在时返回 [404] 错误。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填，受管项 id 或内容链 itemId）")),
                java.util.List.of("itemId"));
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        return searchService.findDetailByAnyId(requiredUuid(arguments, "itemId"));
    }
}
