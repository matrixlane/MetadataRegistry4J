package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.bool;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.enumList;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.enumValue;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.str;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.uuid;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolSchemas.booleanProp;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolSchemas.enumProp;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolSchemas.stringProp;

/**
 * 统一元数据搜索参数（add-mcp-server-support）：{@code search-metadata} 与
 * {@code browse-data-dictionary} 共用——与业务面 /api/v1/metadata/search 及
 * /api/v1/data-dictionary/browser 的语义一致（同服务、同默认分页排序）。
 */
public record MetadataSearchParams(
        String q,
        String type,
        RegistrationStatus status,
        UUID contextId,
        List<Acceptability> acceptability,
        UUID namespaceId,
        boolean exactMatch,
        String identifier,
        String namespaceShorthand,
        Pageable pageable) {

    public static MetadataSearchParams from(Map<String, Object> args) {
        return new MetadataSearchParams(
                str(args, "q"),
                str(args, "type"),
                enumValue(RegistrationStatus.class, args, "status"),
                uuid(args, "contextId"),
                enumList(Acceptability.class, args, "acceptability"),
                uuid(args, "namespaceId"),
                bool(args, "exactMatch", false),
                str(args, "identifier"),
                str(args, "namespaceShorthand"),
                ToolArguments.pageable(args));
    }

    /** 工具 Schema 的 properties（两个工具共用）。 */
    public static Map<String, Object> schemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("q", stringProp("关键词（匹配名称/标识符/定义），可空"));
        properties.put("type", stringProp(
                "实体类型过滤（英文逗号分隔多值，如 DataElement,ValueDomain）。未列入服务白名单的类型自然产生空结果"));
        properties.put("status", enumProp("注册状态过滤",
                Arrays.stream(RegistrationStatus.values()).map(Enum::name).toList()));
        properties.put("contextId", stringProp("上下文 UUID"));
        properties.put("acceptability", stringProp("可接受性过滤（逗号分隔，大小写不敏感）"));
        properties.put("namespaceId", stringProp("命名空间 UUID 限定"));
        properties.put("exactMatch", booleanProp("按标识符精确匹配（配合 identifier），默认 false"));
        properties.put("identifier", stringProp("精确标识符（NS.ID 或当前命名空间短式）"));
        properties.put("namespaceShorthand", stringProp("命名空间短式前缀"));
        properties.put("page", ToolSchemas.integerProp("页码（从 0 起）", 0, 10000));
        properties.put("size", ToolSchemas.integerProp("每页条数（默认 20，上限 100）", 1, 100));
        return properties;
    }
}
