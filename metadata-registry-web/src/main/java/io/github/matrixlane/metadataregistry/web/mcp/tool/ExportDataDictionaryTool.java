package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * {@code export-data-dictionary}（add-mcp-server-support）：数据字典导出。
 * 仅 JSON/CSV 文本形态（二进制 Excel/PDF 不在 MCP 面提供）；复用业务面
 * MetadataExportService 的导出语义。
 */
@Component
public class ExportDataDictionaryTool implements McpTool {

    private final MetadataExportService exportService;

    public ExportDataDictionaryTool(MetadataExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public String name() {
        return "export-data-dictionary";
    }

    @Override
    public String title() {
        return "数据字典导出";
    }

    @Override
    public String description() {
        return "按过滤条件导出注册元数据为 JSON 或 CSV 文本（可选：注册状态、注册机构、条目类型、字段白名单）。"
                + "format 仅允许 json / csv。"
                + "大结果集建议分批：传 page（从 0 起）与 size（1-1000），"
                + "此时 JSON 的 exportMetadata.totalCount 为全量命中数，据此循环翻页。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "format", ToolSchemas.enumProp("导出格式", java.util.List.of("json", "csv")),
                "itemTypes", ToolSchemas.stringProp("条目类型过滤（英文逗号分隔，如 DataElement,ValueDomain）"),
                "registrationStatus", ToolSchemas.stringProp("注册状态过滤（如 RECORDED,QUALIFIED）"),
                "registrationAuthorityId", ToolSchemas.stringProp("注册机构 UUID"),
                "fields", ToolSchemas.stringProp("字段白名单（逗号分隔，最多 50 个）"),
                "page", ToolSchemas.integerProp("分页页码（从 0 起，与 size 同传生效；省略则不分页）", 0, 100_000),
                "size", ToolSchemas.integerProp("分页大小（1-1000，与 page 同传生效；省略则不分页）", 1, 1_000)),
                java.util.List.of("format"));
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        String format = ToolArguments.requiredStr(arguments, "format").toLowerCase(java.util.Locale.ROOT);
        if (!format.equals("json") && !format.equals("csv")) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "format 仅允许 json / csv：" + format);
        }
        MetadataExportRequest request = new MetadataExportRequest(
                parseStatus(arguments),
                ToolArguments.uuid(arguments, "registrationAuthorityId"),
                null, null, null, null, null,
                ToolArguments.stringList(arguments, "fields"),
                ToolArguments.stringList(arguments, "itemTypes"),
                ToolArguments.intArg(arguments, "page", 0, 100_000),
                ToolArguments.intArg(arguments, "size", 1, 1_000));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (format.equals("json")) {
                exportService.exportJsonToStream(request, output);
            } else {
                exportService.exportCsvToStream(request, output);
            }
        } catch (IOException e) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "导出失败：" + e.getMessage());
        }
        return output.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static RegistrationStatus parseStatus(Map<String, Object> arguments) {
        String raw = ToolArguments.str(arguments, "registrationStatus");
        if (raw == null) {
            return null;
        }
        return ToolArguments.enumValue(RegistrationStatus.class, arguments, "registrationStatus");
    }
}
