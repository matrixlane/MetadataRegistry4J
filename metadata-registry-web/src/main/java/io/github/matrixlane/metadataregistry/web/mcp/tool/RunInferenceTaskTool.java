package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantTask;
import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantJobService;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredStr;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.str;
import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.uuid;

/**
 * {@code run-inference-task}（add-mcp-server-support）：运行协作推断任务（受控写入）。
 * 任务清单动态取自既有 AssistantJobService.tasks()（complete-metadata /
 * explain-selection / selection-chat）；推断总开关关闭时由既有层返回
 * "功能未启用"错误（FEATURE_NOT_ENABLED），本工具不另行处理。
 */
@Component
public class RunInferenceTaskTool implements McpTool {

    private final AssistantJobService jobService;
    private final ObjectMapper objectMapper;

    public RunInferenceTaskTool(AssistantJobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "run-inference-task";
    }

    @Override
    public String title() {
        return "运行推断任务";
    }

    @Override
    public String description() {
        return "创建异步协作推断作业（异步执行，经 get-inference-job 查询结果）。"
                + "taskId 取值：complete-metadata（补全定义/指称候选）/ explain-selection（解释选中内容）/"
                + " selection-chat（围绕选中内容对话）。itemId 可选（draft 草稿模式经 params.draft 传参）。"
                + "provider 可选（推断 Agent id）。推断功能未启用时返回功能未启用错误。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "taskId", ToolSchemas.stringProp("任务 id（必填，见描述）"),
                "itemId", ToolSchemas.stringProp("关联条目 UUID（可空；与 params.draft 二选一）"),
                "provider", ToolSchemas.stringProp("推断提供方 id（可空，取默认）"),
                "selection", Map.of("type", "object", "description", "选中内容（field/text/surroundingText/language）"),
                "params", Map.of("type", "object", "description", "任务参数（如 fields、language、draft）")),
                java.util.List.of("taskId"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        String taskId = requiredStr(arguments, "taskId");
        Set<String> available = jobService.tasks().stream()
                .map(AssistantTask::id)
                .collect(Collectors.toSet());
        if (!available.contains(taskId)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "未知任务 taskId：" + taskId + "（可用：" + available + "）");
        }
        UUID itemId = uuid(arguments, "itemId");
        Selection selection = convertNullable(arguments.get("selection"), Selection.class, "selection");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = arguments.get("params") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
        if (arguments.get("params") != null && params == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "params 必须是对象");
        }
        UUID jobId = jobService.createJob(taskId, itemId, selection, params, str(arguments, "provider"));
        return Map.of("jobId", jobId.toString());
    }

    private <T> T convertNullable(Object raw, Class<T> type, String key) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?>)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 必须是对象");
        }
        try {
            return objectMapper.convertValue(raw, type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 结构不合法");
        }
    }
}
