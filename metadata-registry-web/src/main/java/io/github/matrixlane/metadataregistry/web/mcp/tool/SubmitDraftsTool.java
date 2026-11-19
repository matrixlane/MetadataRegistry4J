package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredStr;

/**
 * {@code submit-drafts}（add-mcp-server-support）：批量提交草稿管理项（受控写入）。
 * 逐条校验与逐条结果透传业务面 WorkspaceService.submit 语义；仅归属当前身份
 * 且管理状态为草稿的条目可提交。
 */
@Component
public class SubmitDraftsTool implements McpTool {

    private final WorkspaceService workspaceService;
    private final ActiveRegistrarService activeRegistrarService;
    private final ObjectMapper objectMapper;

    public SubmitDraftsTool(WorkspaceService workspaceService,
                            ActiveRegistrarService activeRegistrarService,
                            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.activeRegistrarService = activeRegistrarService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "submit-drafts";
    }

    @Override
    public String title() {
        return "批量提交草稿";
    }

    @Override
    public String description() {
        return "批量提交草稿管理项（仅管理状态为 draft 且归属当前身份）。返回逐条结果"
                + "（每条独立成败，与业务面 /api/v1/workspace/submit 一致）；"
                + "submission 可携带提交联系人/组织。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemIds", Map.of(
                        "type", "string",
                        "description", "条目 UUID 列表（逗号分隔，必填，最多 100 个）"),
                "submission", Map.of(
                        "type", "object",
                        "description", "提交信息（contactId / organizationId，可选）")),
                List.of("itemIds"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        List<UUID> itemIds = parseItemIds(requiredStr(arguments, "itemIds"));
        SubmissionInfo submission = parseSubmission(arguments.get("submission"));
        return workspaceService.submit(new WorkspaceSubmitRequest(itemIds, submission),
                activeRegistrarService.currentAccountIdOrNull());
    }

    static List<UUID> parseItemIds(String raw) {
        String[] parts = raw.split(",");
        if (parts.length > 100) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "itemIds 最多 100 个：" + parts.length);
        }
        List<UUID> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "非法条目 UUID：" + trimmed);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "itemIds 不能为空");
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private SubmissionInfo parseSubmission(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "submission 必须是对象");
        }
        try {
            return objectMapper.convertValue((Map<String, Object>) map, SubmissionInfo.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "submission 结构不合法（contactId/organizationId）");
        }
    }
}
