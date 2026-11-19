package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJobService;
import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantJobResponse;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.github.matrixlane.metadataregistry.web.mcp.tool.ToolArguments.requiredUuid;

/**
 * {@code get-inference-job}（add-mcp-server-support）：查询推断作业状态与结果。
 * 可见性沿用既有语义：仅创建者或 SUPER_ADMIN。
 */
@Component
public class GetInferenceJobTool implements McpTool {

    private final AssistantJobService jobService;

    public GetInferenceJobTool(AssistantJobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public String name() {
        return "get-inference-job";
    }

    @Override
    public String title() {
        return "查询推断作业";
    }

    @Override
    public String description() {
        return "按 jobId 查询协作推断作业的状态与多态结果；越权访问（非创建者且非超管）由既有校验拒绝。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(
                "jobId", ToolSchemas.stringProp("作业 UUID（必填）")),
                java.util.List.of("jobId"));
    }

    @Override
    public boolean write() {
        return true;
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        return AssistantJobResponse.of(jobService.getJob(requiredUuid(arguments, "jobId")));
    }
}
