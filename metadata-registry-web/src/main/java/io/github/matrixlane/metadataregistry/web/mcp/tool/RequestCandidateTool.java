package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code request-candidate}（add-mcp-server-support）：请求成为候选（受控写入）。
 * 业务面语义：INCOMPLETE 状态、具有主管记录且归属当前身份的管理项可直接推进为 CANDIDATE。
 */
@Component
public class RequestCandidateTool implements McpTool {

    private final WorkspaceService workspaceService;
    private final ActiveRegistrarService activeRegistrarService;
    private final ObjectMapper objectMapper;

    public RequestCandidateTool(WorkspaceService workspaceService,
                                ActiveRegistrarService activeRegistrarService,
                                ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.activeRegistrarService = activeRegistrarService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "request-candidate";
    }

    @Override
    public String title() {
        return "请求成为候选";
    }

    @Override
    public String description() {
        return "将归属当前身份的 INCOMPLETE 管理项请求推进为 CANDIDATE（须具有主管记录）。"
                + "状态/归属不符时由既有校验拒绝。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "itemId", ToolSchemas.stringProp("条目 UUID（必填）"),
                "submissionInfo", Map.of(
                        "type", "object",
                        "description", "提交信息（contactId / organizationId，可选）")),
                java.util.List.of("itemId"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        var itemId = requiredUuid(arguments, "itemId");
        workspaceService.requestCandidate(itemId, parseSubmissionInfo(arguments.get("submissionInfo")),
                activeRegistrarService.currentAccountIdOrNull());
        return Map.of("itemId", itemId.toString(), "result", "CANDIDATE_REQUESTED");
    }

    @SuppressWarnings("unchecked")
    private SubmissionInfo parseSubmissionInfo(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?>)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "submissionInfo 必须是对象");
        }
        return objectMapper.convertValue((Map<String, Object>) raw, SubmissionInfo.class);
    }
}
