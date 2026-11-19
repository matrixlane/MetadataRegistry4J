package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredStr;

/**
 * {@code draft-item}（add-mcp-server-support）：起草注册条目（受控写入）。
 *
 * <p>itemType 白名单 9 类（ObjectClass/Property/DataElementConcept/ConceptualDomain/
 * ValueDomain/DataElement/UnitOfMeasure/MeasureClass/Dimensionality），payload 为
 * 业务面创建 DTO 形态。创建走既有 *Service.create：条目初始注册状态 Incomplete、
 * **不自动提交**——与 UI 手动创建草稿语义一致。缺省 registration 与归属由
 * {@link #ensureRegistrationBlock} 与 {@link McpDraftOwnershipPrefill} 补齐
 * （正交链闭环 + 归属当前身份，两者均为 AGENTS.md 正交链完整性规则的 MCP 延伸）。
 */
@Component
public class DraftItemTool implements McpTool {

    private final DraftItemRoutes routes;
    private final McpDraftOwnershipPrefill ownershipPrefill;

    public DraftItemTool(DraftItemRoutes routes, McpDraftOwnershipPrefill ownershipPrefill) {
        this.routes = routes;
        this.ownershipPrefill = ownershipPrefill;
    }

    @Override
    public String name() {
        return "draft-item";
    }

    @Override
    public String title() {
        return "起草条目";
    }

    @Override
    public String description() {
        return "起草注册条目（不自动提交）：itemType 限定 9 类核心类型（ObjectClass/Property/"
                + "DataElementConcept/ConceptualDomain/ValueDomain/DataElement/UnitOfMeasure/"
                + "MeasureClass/Dimensionality），payload 为对应创建 DTO。创建后条目为草稿语义"
                + "（初始 Incomplete、归属当前身份），须经 submit-drafts 提交后进入治理流程。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemType", ToolSchemas.enumProp("条目类型（必填）", java.util.List.of(
                        "ObjectClass", "Property", "DataElementConcept", "ConceptualDomain",
                        "ValueDomain", "DataElement", "UnitOfMeasure", "MeasureClass", "Dimensionality")),
                "payload", Map.of(
                        "type", "object",
                        "description", "条目字段（结构随 itemType 不同，遵循业务面创建 DTO；含指称/定义行）")),
                List.of("itemType", "payload"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        DraftItemRoutes.Route route = routes.require(requiredStr(arguments, "itemType"));
        Object request = routes.convertAndValidate(
                ownershipPrefill.prefillStewardship(ensureRegistrationBlock(payloadOf(arguments))),
                route);
        return route.creator().apply(request);
    }

    /**
     * 正交链完整性守卫（AGENTS.md 规则延伸）：payload 未携带有效 registration 块时
     * 合成默认块（namespaceId 空 → 按活动 Registrar 推导默认命名空间；identifier/version
     * 空 → 自动策略）；并对显式块强制初始注册状态 INCOMPLETE（draft-item 语义，
     * 业务面 ALLOWED_INITIAL_STATUSES 另允许 APPLICATION/HISTORICAL，MCP 起草不放行）。
     * 缺省/空块产生"裸内容链条目"停在 Identified_Item 正是 2026-10-09 受管链闭环
     * 事故的不变式违反形态——MCP 写路径 MUST NOT 复现（评审 MAJOR-3）。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> ensureRegistrationBlock(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object registration = payload.get("registration");
        Map<String, Object> copy = new java.util.LinkedHashMap<>(payload);
        if (!(registration instanceof Map<?, ?>)) {
            copy.put("registration", Map.of("registrationStatus", "INCOMPLETE"));
            return copy;
        }
        Map<String, Object> registrationMap = new java.util.LinkedHashMap<>(
                (Map<String, Object>) registration);
        Object status = registrationMap.get("registrationStatus");
        if (status != null && !"INCOMPLETE".equalsIgnoreCase(String.valueOf(status))) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                    "draft-item 的初始注册状态必须为 INCOMPLETE（收到：" + status + "）");
        }
        registrationMap.putIfAbsent("registrationStatus", "INCOMPLETE");
        copy.put("registration", registrationMap);
        return copy;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> payloadOf(Map<String, Object> arguments) {
        Object payload = arguments.get("payload");
        if (payload instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
