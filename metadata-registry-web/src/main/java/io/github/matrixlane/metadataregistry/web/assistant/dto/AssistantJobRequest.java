package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

/**
 * 创建协作作业请求体。
 *
 * @param taskId    任务 id（complete-metadata / explain-selection / …）
 * @param itemId    关联注册项目 id（按任务 inputSpec 决定是否必填）
 * @param selection 划选输入（按任务 inputSpec 决定是否必填）
 * @param params    任务参数（任务自定义语义，如 fields / question）
 * @param provider  提供方覆盖（Agent id），可空时用默认配置
 */
@Schema(description = "创建协作作业请求")
public record AssistantJobRequest(
        @Schema(description = "任务 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "taskId 不能为空")
        String taskId,

        @Schema(description = "关联注册项目 id")
        UUID itemId,

        @Schema(description = "划选输入")
        @Valid
        Selection selection,

        @Schema(description = "任务参数")
        Map<String, Object> params,

        @Schema(description = "提供方覆盖：Agent id")
        String provider) {
}
