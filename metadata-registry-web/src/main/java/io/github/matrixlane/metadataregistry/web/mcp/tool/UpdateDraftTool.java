package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredStr;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code update-draft}（add-mcp-server-support）：更新草稿（受控写入）。
 *
 * <p>三重守卫（全部复用既有语义）：
 * <ol>
 *   <li>条目须为受管项且管理状态为草稿/未完成——经既有标量投影
 *       {@code findMetaByItemId} 读 administrativeStatus（DRAFT / INITIAL，
 *       INITIAL 即注册状态 Incomplete 的管理状态落点），不触碰实体继承图
 *       （SQLite 64 表 join 上限规避，同业务面约定）；</li>
 *   <li>归属校验：{@code ActiveRegistrarService.assertCanModifyItem}（弱角色/越权由既有逻辑拒绝）；</li>
 *   <li>payload 经路由转换 + Bean Validation（422）。</li>
 * </ol>
 */
@Component
public class UpdateDraftTool implements McpTool {

    private final DraftItemRoutes routes;
    private final AdministeredItemRepository administeredItemRepository;
    private final ActiveRegistrarService activeRegistrarService;

    public UpdateDraftTool(DraftItemRoutes routes,
                           AdministeredItemRepository administeredItemRepository,
                           ActiveRegistrarService activeRegistrarService) {
        this.routes = routes;
        this.administeredItemRepository = administeredItemRepository;
        this.activeRegistrarService = activeRegistrarService;
    }

    @Override
    public String name() {
        return "update-draft";
    }

    @Override
    public String title() {
        return "更新草稿";
    }

    @Override
    public String description() {
        return "更新草稿条目：仅允许管理状态为草稿/未完成（draft/initial）且归属当前身份的条目。"
                + "itemType + payload 语义同 draft-item。归属不符或状态不符时拒绝且零写入。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填）"),
                "itemType", ToolSchemas.enumProp("条目类型（必填）", java.util.List.of(
                        "ObjectClass", "Property", "DataElementConcept", "ConceptualDomain",
                        "ValueDomain", "DataElement", "UnitOfMeasure", "MeasureClass", "Dimensionality")),
                "payload", Map.of(
                        "type", "object",
                        "description", "条目字段（结构随 itemType 不同，遵循业务面更新 DTO）")),
                List.of("itemId", "itemType", "payload"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        UUID itemId = requiredUuid(arguments, "itemId");
        DraftItemRoutes.Route route = routes.require(requiredStr(arguments, "itemType"));

        List<Object[]> meta = administeredItemRepository.findMetaByItemId(itemId);
        if (meta.isEmpty()) {
            throw new BusinessException(ApiCodes.NOT_FOUND, "条目不存在或不受管：" + itemId);
        }
        Object statusValue = meta.get(0)[6];
        AdministrativeStatus status = statusValue instanceof AdministrativeStatus s ? s : null;
        if (status != AdministrativeStatus.DRAFT && status != AdministrativeStatus.INITIAL) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "仅允许更新草稿/未完成状态的条目（当前管理状态：" + status + "）");
        }

        activeRegistrarService.assertCanModifyItem(itemId);

        Object request = routes.convertAndValidate(DraftItemTool.payloadOf(arguments), route);
        return route.updater().apply(itemId, request);
    }
}
