package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建生成来源记录请求体。生成时间由服务端落库时刻决定，操作者取当前登录用户。
 */
@Schema(description = "创建生成来源记录请求")
public record GenerationRecordRequest(
        @Schema(description = "关联注册项目 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "itemId 不能为空")
        UUID itemId,

        @Schema(description = "协作任务 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "taskId 不能为空")
        String taskId,

        @Schema(description = "提供方（Agent）id")
        String providerId,

        @Schema(description = "Agent 显示名")
        String agentDisplayName,

        @Schema(description = "结果种类：CANDIDATES / TEXT / FINDINGS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "outputKind 不能为空")
        String outputKind,

        @Schema(description = "AI 输出原文", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "rawOutput 不能为空")
        String rawOutput,

        @Schema(description = "动作类型，如 SAVED_TO_EXPLANATORY_COMMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "actionType 不能为空")
        String actionType) {
}
