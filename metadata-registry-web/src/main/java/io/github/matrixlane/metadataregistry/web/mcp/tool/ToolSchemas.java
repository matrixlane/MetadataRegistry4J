package io.github.matrixlane.metadataregistry.web.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具输入 JSON Schema 构造助手（add-mcp-server-support）。
 */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public static Map<String, Object> stringProp(String description) {
        return prop("string", description);
    }

    /** 枚举属性：allowed 取值列表给 Agent 做选择约束。 */
    public static Map<String, Object> enumProp(String description, List<String> allowed) {
        Map<String, Object> prop = prop("string", description);
        prop.put("enum", allowed);
        return prop;
    }

    public static Map<String, Object> integerProp(String description, int minimum, int maximum) {
        Map<String, Object> prop = prop("integer", description);
        prop.put("minimum", minimum);
        prop.put("maximum", maximum);
        return prop;
    }

    public static Map<String, Object> booleanProp(String description) {
        return prop("boolean", description);
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type);
        prop.put("description", description);
        return prop;
    }
}
