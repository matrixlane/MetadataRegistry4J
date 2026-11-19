package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ImpactAnalysisResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDtoConverters;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.str;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.uuid;

/**
 * {@code get-mapping-impact}（add-mcp-server-support）：映射变更影响面分析。
 * 支持两种锚点（二选一必填）：条目变更（itemId）或映射变更（mappingId）。
 */
@Component
public class GetMappingImpactTool implements McpTool {

    private final ImpactAnalysisService impactAnalysisService;

    public GetMappingImpactTool(ImpactAnalysisService impactAnalysisService) {
        this.impactAnalysisService = impactAnalysisService;
    }

    @Override
    public String name() {
        return "get-mapping-impact";
    }

    @Override
    public String title() {
        return "映射影响面分析";
    }

    @Override
    public String description() {
        return "分析变更的级联影响：itemId 锚点查条目变更影响（默认 changeType=UPDATED，最多 3 层），"
                + "mappingId 锚点查映射变更影响（默认 changeType=MODIFIED）。两参数二选一必填。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（与 mappingId 二选一）"),
                "mappingId", ToolSchemas.stringProp("映射 UUID（与 itemId 二选一）"),
                "changeType", ToolSchemas.stringProp("变更类型字符串（默认按锚点取 UPDATED / MODIFIED）")),
                null);
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        UUID itemId = uuid(arguments, "itemId");
        UUID mappingId = uuid(arguments, "mappingId");
        if ((itemId == null) == (mappingId == null)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "itemId 与 mappingId 必须二选一（且仅给一个）");
        }
        ImpactAnalysisResult result = itemId != null
                ? impactAnalysisService.analyzeItemChange(itemId, str(arguments, "changeType") == null
                        ? "UPDATED" : str(arguments, "changeType"))
                : impactAnalysisService.analyzeMappingChange(mappingId, str(arguments, "changeType") == null
                        ? "MODIFIED" : str(arguments, "changeType"));
        return MappingDtoConverters.toImpactResponse(result);
    }
}
