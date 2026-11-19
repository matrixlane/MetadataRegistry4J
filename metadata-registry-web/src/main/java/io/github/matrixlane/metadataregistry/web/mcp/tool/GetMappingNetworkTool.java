package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDtoConverters;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingNetworkResponse;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.enumList;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.intInRange;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.uuid;

/**
 * {@code get-mapping-network}（add-mcp-server-support）：以某条目为中心的
 * N 层映射网络。未给 centerId 时返回空网络（与业务面语义一致）。
 */
@Component
public class GetMappingNetworkTool implements McpTool {

    private final ItemMappingService mappingService;

    public GetMappingNetworkTool(ItemMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @Override
    public String name() {
        return "get-mapping-network";
    }

    @Override
    public String title() {
        return "映射网络";
    }

    @Override
    public String description() {
        return "查询以指定条目为中心的 N 层映射关系网络（节点上限 500，支持按映射类型过滤）。"
                + "centerId 缺省时返回空网络。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "centerId", ToolSchemas.stringProp("中心条目 UUID（可空，空则返回空网络）"),
                "depth", ToolSchemas.integerProp("网络深度（默认 3）", 1, 5),
                "filterTypes", ToolSchemas.stringProp("映射类型过滤（逗号分隔，如 EQUIVALENT,RELATED）")),
                null);
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        UUID centerId = uuid(arguments, "centerId");
        if (centerId == null) {
            return MappingDtoConverters.emptyNetwork();
        }
        int depth = intInRange(arguments, "depth", 1, 5, 3);
        List<MappingDegree> filterTypes = enumList(MappingDegree.class, arguments, "filterTypes");

        ItemMappingService.NetworkGraphResult result =
                mappingService.getNetworkGraph(centerId, depth, filterTypes);
        return MappingDtoConverters.toNetworkResponse(result);
    }
}
